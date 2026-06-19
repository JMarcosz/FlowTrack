import { BadRequestException, Injectable } from "@nestjs/common";
import { GmailIntegrationDoc, GmailOAuthTokensDoc } from "./gmail.types";
import { GmailRepository } from "./gmail.repository";

interface GmailTokenResponse {
  access_token?: string;
  expires_in?: number;
  token_type?: string;
  scope?: string;
}

interface GmailWatchResponse {
  historyId?: string;
  expiration?: string;
}

interface GmailMessagesListResponse {
  messages?: Array<{ id?: string; threadId?: string }>;
  nextPageToken?: string;
  resultSizeEstimate?: number;
}

interface GmailHistoryListResponse {
  history?: Array<{
    id?: string;
    messagesAdded?: Array<{ message?: { id?: string; threadId?: string } }>;
  }>;
  nextPageToken?: string;
  historyId?: string;
}

interface GmailMessage {
  id?: string;
  threadId?: string;
  labelIds?: string[];
  snippet?: string;
  historyId?: string;
  internalDate?: string;
  payload?: GmailMessagePart;
}

interface GmailProfile {
  emailAddress?: string;
  messagesTotal?: number;
  threadsTotal?: number;
  historyId?: string;
}

interface GmailMessagePart {
  partId?: string;
  mimeType?: string;
  filename?: string;
  headers?: Array<{ name?: string; value?: string }>;
  body?: { size?: number; data?: string; attachmentId?: string };
  parts?: GmailMessagePart[];
}

export class GmailApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly responseText: string,
  ) {
    super(message);
  }
}

@Injectable()
export class GmailApiService {
  constructor(private readonly gmailRepository: GmailRepository) {}

  async watch(uid: string): Promise<GmailWatchResponse> {
    const topicName = process.env.GMAIL_PUBSUB_TOPIC_NAME;
    if (!topicName) {
      throw new BadRequestException("Missing GMAIL_PUBSUB_TOPIC_NAME.");
    }

    const response = await this.gmailFetchJson<GmailWatchResponse>(uid, "POST", "/gmail/v1/users/me/watch", {
      topicName,
    });
    return response;
  }

  async listMessages(uid: string, query: string, pageToken?: string): Promise<GmailMessagesListResponse> {
    const search = new URLSearchParams();
    search.set("maxResults", "500");
    if (query.trim()) search.set("q", query.trim());
    if (pageToken) search.set("pageToken", pageToken);
    return this.gmailFetchJson<GmailMessagesListResponse>(uid, "GET", `/gmail/v1/users/me/messages?${search.toString()}`);
  }

  async getMessage(uid: string, messageId: string): Promise<GmailMessage> {
    const search = new URLSearchParams();
    search.set("format", "full");
    return this.gmailFetchJson<GmailMessage>(uid, "GET", `/gmail/v1/users/me/messages/${encodeURIComponent(messageId)}?${search.toString()}`);
  }

  async listHistory(uid: string, startHistoryId: string, pageToken?: string): Promise<GmailHistoryListResponse> {
    const search = new URLSearchParams();
    search.set("startHistoryId", startHistoryId);
    search.set("maxResults", "500");
    if (pageToken) search.set("pageToken", pageToken);
    return this.gmailFetchJson<GmailHistoryListResponse>(uid, "GET", `/gmail/v1/users/me/history?${search.toString()}`);
  }

  async getProfile(uid: string): Promise<GmailProfile> {
    return this.gmailFetchJson<GmailProfile>(uid, "GET", "/gmail/v1/users/me/profile");
  }

  async buildEmailEnvelope(uid: string, message: GmailMessage) {
    const headers = extractHeaders(message.payload);
    const body = extractBodyText(message.payload);
    const subject = headers.subject ?? "";
    const from = headers.from ?? "";
    const date = headers.date ?? message.internalDate ?? undefined;
    const receivedAt = date ? new Date(date).toISOString() : undefined;

    return {
      uidUsuario: uid,
      uid,
      sourceEventId: message.id ?? undefined,
      sourceMessageId: message.id ?? undefined,
      sourceTransactionId: headers["message-id"] ?? message.id ?? undefined,
      bankCode: headers.from?.includes("banreservas") ? "BANRESERVAS" : undefined,
      from,
      subject,
      snippet: message.snippet ?? "",
      body,
      read: message.labelIds?.includes("UNREAD") ? false : true,
      labels: message.labelIds ?? [],
      receivedAt,
      sentAt: date ? new Date(date).toISOString() : undefined,
      threadId: message.threadId,
      historyId: message.historyId,
      channel: "email_ingestion" as const,
      payload: {
        messageId: message.id,
        threadId: message.threadId,
        labels: message.labelIds ?? [],
        headers,
        body,
        snippet: message.snippet ?? "",
      },
    };
  }

  async getIntegrationTokens(uid: string): Promise<GmailOAuthTokensDoc> {
    const tokens = await this.gmailRepository.getTokens(uid);
    if (!tokens) {
      throw new BadRequestException("Gmail OAuth tokens are not configured for this user.");
    }
    return tokens;
  }

  async getIntegration(uid: string): Promise<GmailIntegrationDoc | null> {
    return this.gmailRepository.getIntegration(uid);
  }

  private async gmailFetchJson<T>(uid: string, method: "GET" | "POST", path: string, body?: unknown): Promise<T> {
    const response = await this.gmailFetch(uid, method, path, body);
    return response.json() as Promise<T>;
  }

  private async gmailFetch(uid: string, method: "GET" | "POST", path: string, body?: unknown): Promise<Response> {
    const accessToken = await this.resolveAccessToken(uid);
    const url = `https://gmail.googleapis.com${path}`;
    const execute = async (token: string) => fetch(url, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: body ? JSON.stringify(body) : undefined,
    });

    let response = await this.withRetry(() => execute(accessToken), `gmail ${path}`);
    if (response.status === 401) {
      const refreshed = await this.refreshAccessToken(uid);
      response = await this.withRetry(() => execute(refreshed), `gmail ${path} retry`);
    }

    this.assertOk(response, path);
    return response;
  }

  private async resolveAccessToken(uid: string): Promise<string> {
    const tokens = await this.getIntegrationTokens(uid);
    const validUntil = tokens.expiryDate ? new Date(tokens.expiryDate).getTime() : 0;
    if (tokens.accessToken && validUntil - Date.now() > 60_000) {
      return tokens.accessToken;
    }
    return this.refreshAccessToken(uid);
  }

  private async refreshAccessToken(uid: string): Promise<string> {
    const tokens = await this.getIntegrationTokens(uid);
    if (!tokens.refreshToken) {
      throw new BadRequestException("Gmail refresh token is missing.");
    }

    const clientId = process.env.GMAIL_OAUTH_CLIENT_ID;
    const clientSecret = process.env.GMAIL_OAUTH_CLIENT_SECRET;
    if (!clientId || !clientSecret) {
      throw new BadRequestException("Gmail OAuth client configuration is incomplete.");
    }

    const body = new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      refresh_token: tokens.refreshToken,
      grant_type: "refresh_token",
    });

    const response = await this.withRetry(() => fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body,
    }), "oauth2 token refresh");
    this.assertOk(response, "oauth2 token refresh");

    const data = await response.json() as GmailTokenResponse;
    const accessToken = data.access_token ?? null;
    const expiresIn = data.expires_in ?? 3600;
    if (!accessToken) {
      throw new BadRequestException("Token refresh did not return an access token.");
    }

    await this.gmailRepository.saveTokens(uid, {
      accessToken,
      refreshToken: tokens.refreshToken,
      scope: data.scope ?? tokens.scope,
      tokenType: data.token_type ?? tokens.tokenType,
      expiryDate: new Date(Date.now() + expiresIn * 1000).toISOString(),
    });

    return accessToken;
  }

  private assertOk(response: Response, context: string) {
    if (response.ok) return;
    throw new GmailApiError(
      `Gmail API request failed for ${context}: ${response.status} ${response.statusText}`,
      response.status,
      "",
    );
  }

  private async withRetry<T>(operation: () => Promise<T>, context: string, attempts = 3): Promise<T> {
    let lastError: unknown;
    for (let attempt = 1; attempt <= attempts; attempt += 1) {
      try {
        const result = await operation();
        if (result instanceof Response && [429, 500, 502, 503, 504].includes(result.status)) {
          lastError = new GmailApiError(`${context} returned ${result.status}`, result.status, "");
          if (attempt < attempts) {
            await sleep(backoffMs(attempt));
            continue;
          }
        }
        return result;
      } catch (error) {
        lastError = error;
        if (attempt < attempts && isRetryableError(error)) {
          await sleep(backoffMs(attempt));
          continue;
        }
        throw error;
      }
    }
    throw lastError instanceof Error ? lastError : new Error(`${context} failed after ${attempts} attempts`);
  }
}

function extractHeaders(payload?: GmailMessagePart): Record<string, string> {
  const headers: Record<string, string> = {};
  for (const header of payload?.headers ?? []) {
    const name = header.name?.toLowerCase();
    const value = header.value?.trim();
    if (!name || !value) continue;
    headers[name] = value;
  }
  return headers;
}

function extractBodyText(part?: GmailMessagePart): string {
  if (!part) return "";
  const direct = decodeBase64Url(part.body?.data);
  if (part.mimeType?.startsWith("text/plain") && direct) {
    return direct;
  }

  const childParts = part.parts ?? [];
  for (const child of childParts) {
    const text = extractBodyText(child);
    if (text) return text;
  }

  if (part.mimeType?.startsWith("text/html") && direct) {
    return stripHtml(direct);
  }

  return direct;
}

function decodeBase64Url(value?: string): string {
  if (!value) return "";
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  try {
    return Buffer.from(padded, "base64").toString("utf8");
  } catch {
    return "";
  }
}

function stripHtml(value: string): string {
  return value
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<\/p>|<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/\s+/g, " ")
    .trim();
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function backoffMs(attempt: number) {
  return Math.min(1000 * 2 ** (attempt - 1), 8000);
}

function isRetryableError(error: unknown): boolean {
  if (error instanceof GmailApiError) {
    return [429, 500, 502, 503, 504].includes(error.status);
  }
  return false;
}
