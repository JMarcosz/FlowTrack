import { Injectable, UnauthorizedException } from "@nestjs/common";
import { appCheck } from "./firebase-admin";

export interface GatewayIdentity {
  uid: string;
  email?: string;
  issuer?: string;
  audience?: string;
  raw: Record<string, unknown>;
}

export interface VerifiedAppCheck {
  appId?: string;
  tokenHeader: string;
}

@Injectable()
export class RequestAuthService {
  requireGatewayIdentity(headers: Record<string, string | undefined>): GatewayIdentity {
    const encoded = headers["x-apigateway-api-userinfo"] ?? headers["X-Apigateway-Api-Userinfo"];
    if (!encoded) {
      throw new UnauthorizedException("Missing X-Apigateway-Api-Userinfo header.");
    }

    const raw = decodeBase64UrlJson(encoded);
    const uid = asString(raw.uid) ?? asString(raw.sub) ?? asString(raw.user_id) ?? asString(raw.email);
    if (!uid) {
      throw new UnauthorizedException("Gateway userinfo did not include a stable uid.");
    }

    return {
      uid,
      email: asString(raw.email) ?? undefined,
      issuer: asString(raw.iss) ?? undefined,
      audience: asString(raw.aud) ?? undefined,
      raw,
    };
  }

  async requireAppCheck(headers: Record<string, string | undefined>): Promise<VerifiedAppCheck> {
    const token = headers["x-firebase-appcheck"] ?? headers["X-Firebase-AppCheck"];
    if (!token) {
      throw new UnauthorizedException("Missing X-Firebase-AppCheck header.");
    }

    await appCheck().verifyToken(token);
    return { appId: undefined, tokenHeader: token };
  }

  readOptionalGatewayIdentity(headers: Record<string, string | undefined>): GatewayIdentity | null {
    try {
      return this.requireGatewayIdentity(headers);
    } catch {
      return null;
    }
  }
}

function decodeBase64UrlJson(input: string): Record<string, unknown> {
  try {
    const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const json = Buffer.from(padded, "base64").toString("utf8");
    const parsed = JSON.parse(json);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
  } catch {
    // fall through
  }
  throw new UnauthorizedException("Invalid X-Apigateway-Api-Userinfo payload.");
}

function asString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value : null;
}
