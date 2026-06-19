import { Injectable } from "@nestjs/common";
import { firestore } from "../../common/firebase-admin";
import { GmailConnectionState, GmailDateRange, GmailIntegrationDoc, GmailOAuthTokensDoc, GmailReadFilter } from "./gmail.types";

@Injectable()
export class GmailRepository {
  private readonly db = firestore();

  private integrationRef(uid: string) {
    return this.db.collection("usuarios").doc(uid).collection("integraciones").doc("gmail");
  }

  private tokensRef(uid: string) {
    return this.db.collection("backendOAuthTokens").doc(uid);
  }

  private eventsRef(sourceEventId: string) {
    return this.db.collection("backendEmailEvents").doc(sourceEventId);
  }

  async upsertIntegration(uid: string, patch: Partial<GmailIntegrationDoc> & Pick<GmailIntegrationDoc, "uidUsuario">) {
    const now = new Date().toISOString();
    const current = await this.getIntegration(uid);
    const doc: GmailIntegrationDoc = {
      uidUsuario: uid,
      estado: patch.estado ?? current?.estado ?? "idle",
      readFilter: patch.readFilter ?? current?.readFilter ?? "both",
      dateRange: patch.dateRange ?? current?.dateRange ?? null,
      lastSyncAt: patch.lastSyncAt ?? current?.lastSyncAt ?? null,
      watchExpirationAt: patch.watchExpirationAt ?? current?.watchExpirationAt ?? null,
      connectedAt: patch.connectedAt ?? current?.connectedAt ?? null,
      disconnectedAt: patch.disconnectedAt ?? current?.disconnectedAt ?? null,
      createdAt: current?.createdAt ?? now,
      updatedAt: now,
    };
    await this.integrationRef(uid).set(doc, { merge: true });
    return doc;
  }

  async getIntegration(uid: string): Promise<GmailIntegrationDoc | null> {
    const snap = await this.integrationRef(uid).get();
    return snap.exists ? (snap.data() as GmailIntegrationDoc) : null;
  }

  async saveTokens(uid: string, tokens: Partial<GmailOAuthTokensDoc>) {
    const now = new Date().toISOString();
    const snap = await this.tokensRef(uid).get();
    const doc: GmailOAuthTokensDoc = {
      uidUsuario: uid,
      accessToken: tokens.accessToken ?? null,
      refreshToken: tokens.refreshToken ?? (snap.exists ? (snap.data() as GmailOAuthTokensDoc).refreshToken : null),
      scope: tokens.scope ?? null,
      tokenType: tokens.tokenType ?? null,
      expiryDate: tokens.expiryDate ?? null,
      createdAt: snap.exists ? (snap.data() as GmailOAuthTokensDoc).createdAt : now,
      updatedAt: now,
    };
    await this.tokensRef(uid).set(doc, { merge: true });
    return doc;
  }

  async getTokens(uid: string): Promise<GmailOAuthTokensDoc | null> {
    const snap = await this.tokensRef(uid).get();
    return snap.exists ? (snap.data() as GmailOAuthTokensDoc) : null;
  }

  async deleteTokens(uid: string) {
    await this.tokensRef(uid).delete();
  }

  async recordEvent(sourceEventId: string, data: Record<string, unknown>) {
    await this.eventsRef(sourceEventId).set(
      {
        ...data,
        sourceEventId,
        updatedAt: new Date().toISOString(),
      },
      { merge: true },
    );
  }

  async markWatch(uid: string, watchExpirationAt: string) {
    return this.upsertIntegration(uid, {
      uidUsuario: uid,
      watchExpirationAt,
    });
  }

  async markConnected(uid: string, readFilter: GmailReadFilter, dateRange: GmailDateRange | null) {
    return this.upsertIntegration(uid, {
      uidUsuario: uid,
      estado: "connected" as GmailConnectionState,
      readFilter,
      dateRange,
      connectedAt: new Date().toISOString(),
      disconnectedAt: null,
      lastSyncAt: null,
    });
  }

  async markDisconnected(uid: string) {
    await this.deleteTokens(uid);
    return this.upsertIntegration(uid, {
      uidUsuario: uid,
      estado: "disconnected",
      disconnectedAt: new Date().toISOString(),
    });
  }

  async markSync(uid: string, lastSyncAt: string) {
    return this.upsertIntegration(uid, {
      uidUsuario: uid,
      estado: "idle",
      lastSyncAt,
    });
  }
}
