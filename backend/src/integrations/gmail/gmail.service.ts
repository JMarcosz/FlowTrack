import { BadRequestException, Injectable, Logger, UnauthorizedException } from "@nestjs/common";
import { isModeAllowed, currentServiceMode } from "../../common/service-mode";
import { RequestAuthService } from "../../common/auth.service";
import { GmailRepository } from "./gmail.repository";
import { GmailDateRange, GmailReadFilter } from "./gmail.types";
import { GmailStateService } from "./gmail-state.service";
import { GmailIngestionService } from "./gmail-ingestion.service";
import { randomUUID } from "crypto";

export type GmailSyncState = "idle" | "connected" | "syncing" | "error" | "disconnected";

@Injectable()
export class GmailService {
  private readonly logger = new Logger(GmailService.name);

  constructor(
    private readonly authService: RequestAuthService,
    private readonly gmailRepository: GmailRepository,
    private readonly stateService: GmailStateService,
    private readonly ingestionService: GmailIngestionService,
  ) {}

  async startConnect(headers: Record<string, string | undefined>, body: StartConnectRequest) {
    this.assertMode("web");
    const identity = this.authService.requireGatewayIdentity(headers);
    await this.authService.requireAppCheck(headers);

    const readFilter = normalizeReadFilter(body.readFilter);
    const dateRange = normalizeDateRange(body.dateRange);
    const stateToken = this.stateService.create(identity.uid, {
      readFilter,
      dateRange,
      redirectPath: body.redirectPath,
      ttlMinutes: body.ttlMinutes ?? 15,
    });
    const authorizationUrl = this.buildAuthorizationUrl(stateToken, body.redirectPath);

    await this.gmailRepository.upsertIntegration(identity.uid, {
      uidUsuario: identity.uid,
      estado: "idle",
      readFilter,
      dateRange,
    });

    return {
      action: "start_connect",
      uid: identity.uid,
      authorizationUrl,
      stateToken,
      serviceMode: currentServiceMode(),
    };
  }

  async handleCallback(query: Record<string, string | undefined>) {
    this.assertMode("web");
    const code = query.code;
    const state = query.state;
    if (!code) throw new BadRequestException("Missing OAuth code.");
    if (!state) throw new BadRequestException("Missing OAuth state.");

    const payload = this.stateService.verify(state);
    const tokenResponse = await this.exchangeCodeForTokens(code);
    const readFilter = payload.readFilter;
    const dateRange = payload.dateRange ?? null;

    await this.gmailRepository.saveTokens(payload.uid, tokenResponse);
    await this.gmailRepository.markConnected(payload.uid, readFilter, dateRange);

    return {
      action: "oauth_callback",
      uid: payload.uid,
      connected: true,
      redirectTo: payload.redirectPath,
      authorizationCodeReceived: true,
      tokenStored: true,
    };
  }

  async getStatus(headers: Record<string, string | undefined>) {
    this.assertMode("web");
    const identity = this.authService.requireGatewayIdentity(headers);
    await this.authService.requireAppCheck(headers);
    const doc = await this.gmailRepository.getIntegration(identity.uid);
    return {
      uid: identity.uid,
      state: doc?.estado ?? "idle",
      lastSyncAt: doc?.lastSyncAt ?? null,
      readFilter: doc?.readFilter ?? "both",
      dateRange: doc?.dateRange ?? null,
      watchExpirationAt: doc?.watchExpirationAt ?? null,
      connectedAt: doc?.connectedAt ?? null,
    };
  }

  async disconnect(headers: Record<string, string | undefined>) {
    this.assertMode("web");
    const identity = this.authService.requireGatewayIdentity(headers);
    await this.authService.requireAppCheck(headers);
    const doc = await this.gmailRepository.markDisconnected(identity.uid);
    return { action: "disconnect", uid: identity.uid, disconnected: doc.estado === "disconnected" };
  }

  async requestSync(headers: Record<string, string | undefined>, body: SyncRequestBody) {
    this.assertMode("web");
    const identity = this.authService.requireGatewayIdentity(headers);
    await this.authService.requireAppCheck(headers);
    const readFilter = body.readFilter ? normalizeReadFilter(body.readFilter) : undefined;
    const dateRange = body.dateRange ? normalizeDateRange(body.dateRange) : undefined;
    const current = await this.gmailRepository.getIntegration(identity.uid);
    if (!current) {
      throw new BadRequestException("Gmail integration is not configured for this user.");
    }

    await this.gmailRepository.upsertIntegration(identity.uid, {
      uidUsuario: identity.uid,
      estado: "syncing",
      readFilter: readFilter ?? current.readFilter,
      dateRange: dateRange ?? current.dateRange,
    });

    const requestId = randomUUID();
    await this.gmailRepository.recordEvent(`sync:${identity.uid}:${requestId}`, {
      kind: "sync_request",
      uidUsuario: identity.uid,
      readFilter: readFilter ?? current.readFilter,
      dateRange: dateRange ?? current.dateRange,
      requestedAt: new Date().toISOString(),
    });

    return {
      action: "sync_requested",
      uid: identity.uid,
      requestId,
      queued: true,
      source: "android",
      readFilter: readFilter ?? current.readFilter,
      dateRange: dateRange ?? current.dateRange,
    };
  }

  async handlePubSubGmail(payload: unknown) {
    this.assertMode("webhook");
    return this.ingestionService.ingest(payload, "pubsub");
  }

  async handleEmailIngestion(payload: unknown) {
    this.assertMode("webhook");
    return this.ingestionService.ingest(payload, "email_ingestion");
  }

  async renewWatch(payload: unknown) {
    this.assertMode("worker");
    const event = normalizeUnknownRecord(payload);
    const uid = readString(event.uid) ?? readString(event.uidUsuario);
    const watchExpirationAt = readString(event.watchExpirationAt);
    if (!uid || !watchExpirationAt) {
      throw new BadRequestException("Missing uid or watchExpirationAt.");
    }

    const updated = await this.gmailRepository.markWatch(uid, watchExpirationAt);
    await this.gmailRepository.recordEvent(`watch:${uid}:${watchExpirationAt}`, {
      kind: "watch_renew",
      payload: event,
      receivedAt: new Date().toISOString(),
    });

    return { action: "watch_renew", accepted: true, uid, watchExpirationAt: updated.watchExpirationAt };
  }

  private async exchangeCodeForTokens(code: string) {
    const clientId = process.env.GMAIL_OAUTH_CLIENT_ID;
    const clientSecret = process.env.GMAIL_OAUTH_CLIENT_SECRET;
    const redirectUri = process.env.GMAIL_OAUTH_REDIRECT_URI;
    if (!clientId || !clientSecret || !redirectUri) {
      throw new BadRequestException("Gmail OAuth client configuration is incomplete.");
    }

    const body = new URLSearchParams({
      code,
      client_id: clientId,
      client_secret: clientSecret,
      redirect_uri: redirectUri,
      grant_type: "authorization_code",
    });

    const response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    });
    if (!response.ok) {
      const text = await response.text();
      throw new BadRequestException(`Gmail token exchange failed: ${text}`);
    }

    const data = await response.json() as {
      access_token?: string;
      refresh_token?: string;
      scope?: string;
      token_type?: string;
      expires_in?: number;
    };

    const expiryDate = data.expires_in ? new Date(Date.now() + data.expires_in * 1000).toISOString() : null;
    return {
      accessToken: data.access_token ?? null,
      refreshToken: data.refresh_token ?? null,
      scope: data.scope ?? null,
      tokenType: data.token_type ?? null,
      expiryDate,
    };
  }

  private buildAuthorizationUrl(stateToken: string, redirectPath?: string) {
    const clientId = process.env.GMAIL_OAUTH_CLIENT_ID;
    const redirectUri = process.env.GMAIL_OAUTH_REDIRECT_URI;
    const scope = process.env.GMAIL_OAUTH_SCOPES ?? [
      "https://www.googleapis.com/auth/gmail.readonly",
    ].join(" ");
    const prompt = process.env.GMAIL_OAUTH_PROMPT ?? "consent";
    if (!clientId || !redirectUri) {
      throw new BadRequestException("Gmail OAuth client configuration is incomplete.");
    }

    const url = new URL("https://accounts.google.com/o/oauth2/v2/auth");
    url.searchParams.set("client_id", clientId);
    url.searchParams.set("redirect_uri", redirectUri);
    url.searchParams.set("response_type", "code");
    url.searchParams.set("access_type", "offline");
    url.searchParams.set("prompt", prompt);
    url.searchParams.set("include_granted_scopes", "true");
    url.searchParams.set("scope", scope);
    url.searchParams.set("state", stateToken);
    return url.toString();
  }

  private assertMode(required: "web" | "webhook" | "worker") {
    if (!isModeAllowed(required)) {
      throw new UnauthorizedException(`Endpoint unavailable in ${currentServiceMode()} mode.`);
    }
  }
}

export interface StartConnectRequest {
  readFilter?: GmailReadFilter;
  dateRange?: GmailDateRange | null;
  redirectPath?: string;
  ttlMinutes?: number;
}

export interface SyncRequestBody {
  readFilter?: GmailReadFilter;
  dateRange?: GmailDateRange | null;
}

function normalizeReadFilter(value?: string): GmailReadFilter {
  if (value === "read" || value === "unread" || value === "both") return value;
  return "both";
}

function normalizeDateRange(value?: { startDate?: string; endDate?: string } | null): GmailDateRange | null {
  if (!value) return null;
  return {
    startDate: value.startDate?.trim() || undefined,
    endDate: value.endDate?.trim() || undefined,
  };
}

function normalizeUnknownRecord(value: unknown): Record<string, unknown> {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return {};
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function readSourceEventId(record: Record<string, unknown>): string | null {
  return readString(record.sourceEventId) ?? readString(record.messageId) ?? readString(record.eventId) ?? null;
}
