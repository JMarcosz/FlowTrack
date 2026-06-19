import { BadRequestException, Injectable } from "@nestjs/common";
import { createHmac, randomBytes, timingSafeEqual } from "crypto";
import { GmailDateRange, GmailStatePayload, GmailReadFilter } from "./gmail.types";

@Injectable()
export class GmailStateService {
  private readonly secret = process.env.GMAIL_STATE_SECRET ?? process.env.FIREBASE_PROJECT_ID ?? "flowtrack-dev-state-secret";

  create(uid: string, options?: { readFilter?: GmailReadFilter; dateRange?: GmailDateRange | null; redirectPath?: string; ttlMinutes?: number }): string {
    const now = Date.now();
    const payload: GmailStatePayload = {
      uid,
      nonce: randomBytes(12).toString("base64url"),
      readFilter: options?.readFilter ?? "both",
      dateRange: options?.dateRange ?? null,
      redirectPath: options?.redirectPath ?? "flowtrack://oauth/gmail",
      issuedAt: now,
      expiresAt: now + (options?.ttlMinutes ?? 15) * 60_000,
    };
    return this.sign(payload);
  }

  verify(token: string): GmailStatePayload {
    const [payloadPart, sigPart] = token.split(".");
    if (!payloadPart || !sigPart) {
      throw new BadRequestException("Invalid OAuth state.");
    }

    const expectedSig = this.signBytes(payloadPart);
    const actualSig = Buffer.from(sigPart, "base64url");
    if (expectedSig.length !== actualSig.length || !timingSafeEqual(expectedSig, actualSig)) {
      throw new BadRequestException("OAuth state signature mismatch.");
    }

    const payload = JSON.parse(Buffer.from(payloadPart, "base64url").toString("utf8")) as GmailStatePayload;
    if (!payload.uid || !payload.nonce) {
      throw new BadRequestException("OAuth state is incomplete.");
    }

    if (payload.expiresAt < Date.now()) {
      throw new BadRequestException("OAuth state expired.");
    }

    return payload;
  }

  private sign(payload: GmailStatePayload): string {
    const payloadPart = Buffer.from(JSON.stringify(payload), "utf8").toString("base64url");
    const sigPart = this.signBytes(payloadPart).toString("base64url");
    return `${payloadPart}.${sigPart}`;
  }

  private signBytes(payloadPart: string): Buffer {
    return createHmac("sha256", this.secret).update(payloadPart).digest();
  }
}
