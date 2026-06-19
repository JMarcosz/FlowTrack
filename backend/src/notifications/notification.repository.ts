import { Injectable } from "@nestjs/common";
import { firestore } from "../common/firebase-admin";

export interface NotificationPayload {
  title: string;
  body: string;
  route: string;
  channelId: string;
  notificationId: string;
}

@Injectable()
export class NotificationRepository {
  private readonly db = firestore();

  private emitMarkerRef(uid: string, eventId: string) {
    return this.db.doc(`usuarios/${uid}/notificacionesEmitidas/${eventId}`);
  }

  private devicesRef(uid: string) {
    return this.db.collection(`usuarios/${uid}/dispositivos`);
  }

  private budgetsRef(uid: string) {
    return this.db.collection(`usuarios/${uid}/presupuestos`);
  }

  private transactionsRef(uid: string) {
    return this.db.collection(`usuarios/${uid}/transacciones`);
  }

  async emitOnceAndPush(uid: string, eventId: string, payload: NotificationPayload): Promise<boolean> {
    const markerRef = this.emitMarkerRef(uid, eventId);
    const shouldSend = await this.db.runTransaction(async (trx) => {
      const marker = await trx.get(markerRef);
      if (marker.exists) return false;
      trx.set(markerRef, {
        createdAt: new Date(),
        payload,
      });
      return true;
    });

    if (!shouldSend) return false;

    const devicesSnap = await this.devicesRef(uid)
      .where("activo", "==", true)
      .get();

    const deviceTokens = devicesSnap.docs
      .map((doc) => ({
        doc,
        token: String(doc.data().tokenFcm ?? "").trim(),
      }))
      .filter((entry) => entry.token.length > 0);

    if (deviceTokens.length === 0) return true;

    const message: import("firebase-admin/messaging").MulticastMessage = {
      tokens: deviceTokens.map((entry) => entry.token),
      data: {
        title: payload.title,
        body: payload.body,
        route: payload.route,
        channelId: payload.channelId,
        notificationId: payload.notificationId,
      },
    };

    const response = await import("firebase-admin").then((admin) => admin.messaging().sendEachForMulticast(message));
    await this.cleanupInvalidTokens(deviceTokens, response.responses);
    return true;
  }

  async listActiveBudgetRules(uid: string, categoriaId: string) {
    const snapshot = await this.budgetsRef(uid)
      .where("activo", "==", true)
      .where("categoriaId", "==", categoriaId)
      .get();
    return snapshot.docs.map((doc) => doc.data());
  }

  async sumCategoryDebit(uid: string, categoriaId: string, inicio: Date, fin: Date) {
    const snapshot = await this.transactionsRef(uid)
      .where("tipo", "==", "DEBITO")
      .where("categoriaId", "==", categoriaId)
      .where("fecha", ">=", inicio)
      .where("fecha", "<=", fin)
      .get();

    return snapshot.docs.reduce((acc, doc) => acc + asNumber(doc.data().monto), 0);
  }

  private async cleanupInvalidTokens(
    deviceTokens: Array<{ doc: FirebaseFirestore.QueryDocumentSnapshot; token: string }>,
    responses: import("firebase-admin").messaging.BatchResponse["responses"],
  ): Promise<void> {
    const batch = this.db.batch();
    let dirty = false;

    responses.forEach((resp, index) => {
      if (resp.success) return;
      const code = resp.error?.code ?? "";
      if (!code.includes("registration-token-not-registered") && !code.includes("invalid-argument")) return;
      batch.set(deviceTokens[index].doc.ref, { activo: false }, { merge: true });
      dirty = true;
    });

    if (dirty) {
      await batch.commit();
    }
  }
}

function asNumber(value: unknown): number {
  if (typeof value === "number") return value;
  if (typeof value === "string") return Number.parseFloat(value);
  return Number.NaN;
}
