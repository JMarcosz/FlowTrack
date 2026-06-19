import { Injectable } from "@nestjs/common";
import { Timestamp } from "firebase-admin/firestore";
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

  private accountRef(uid: string, accountId: string) {
    return this.db.collection("usuarios").doc(uid).collection("cuentas").doc(accountId);
  }

  private cardRef(uid: string, cardId: string) {
    return this.db.collection("usuarios").doc(uid).collection("tarjetas").doc(cardId);
  }

  private transactionRef(uid: string, transactionId: string) {
    return this.db.collection("usuarios").doc(uid).collection("transacciones").doc(transactionId);
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

  async recordEvent(sourceEventId: string, data: Record<string, unknown>, ttlDays = 30) {
    const now = Timestamp.now();
    await this.eventsRef(sourceEventId).set(
      {
        ...data,
        sourceEventId,
        updatedAt: now,
        createdAt: data.createdAt ?? now,
        expiresAt: Timestamp.fromDate(new Date(Date.now() + ttlDays * 24 * 60 * 60 * 1000)),
      },
      { merge: true },
    );
  }

  async getEvent(sourceEventId: string) {
    const snap = await this.eventsRef(sourceEventId).get();
    return snap.exists ? snap.data() : null;
  }

  async upsertAccount(uid: string, accountId: string, doc: Record<string, unknown>) {
    const snap = await this.accountRef(uid, accountId).get();
    const createdAt = snap.exists ? snap.data()?.createdAt ?? Timestamp.now() : Timestamp.now();
    await this.accountRef(uid, accountId).set(
      {
        ...doc,
        id: accountId,
        uidUsuario: uid,
        createdAt,
        updatedAt: Timestamp.now(),
      },
      { merge: true },
    );
  }

  async upsertCard(uid: string, cardId: string, doc: Record<string, unknown>) {
    const snap = await this.cardRef(uid, cardId).get();
    const createdAt = snap.exists ? snap.data()?.createdAt ?? Timestamp.now() : Timestamp.now();
    await this.cardRef(uid, cardId).set(
      {
        ...doc,
        id: cardId,
        uidUsuario: uid,
        createdAt,
        updatedAt: Timestamp.now(),
      },
      { merge: true },
    );
  }

  async upsertTransaction(uid: string, transactionId: string, doc: Record<string, unknown>) {
    const snap = await this.transactionRef(uid, transactionId).get();
    const createdAt = snap.exists ? snap.data()?.creadoEn ?? Timestamp.now() : Timestamp.now();
    await this.transactionRef(uid, transactionId).set(
      {
        ...doc,
        id: transactionId,
        uidUsuario: uid,
        creadoEn: createdAt,
        actualizadoEn: Timestamp.now(),
      },
      { merge: true },
    );
  }

  async getTransaction(uid: string, transactionId: string) {
    const snap = await this.transactionRef(uid, transactionId).get();
    return snap.exists ? snap.data() : null;
  }

  async listRecentTransactions(uid: string, limit = 200) {
    const snapshot = await this.db
      .collection("usuarios")
      .doc(uid)
      .collection("transacciones")
      .orderBy("fecha", "desc")
      .limit(limit)
      .get();
    return snapshot.docs.map((doc) => doc.data());
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
