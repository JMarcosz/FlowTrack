import * as admin from "firebase-admin";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";

admin.initializeApp();

type PushPayload = {
  title: string;
  body: string;
  route: string;
  channelId: string;
  notificationId: string;
};

const db = admin.firestore();

export const onCargaWritten = onDocumentWritten("usuarios/{uid}/cargas/{cargaId}", async (event) => {
  const uid = event.params.uid as string;
  const cargaId = event.params.cargaId as string;
  const after = event.data?.after;
  if (!after?.exists) return;

  const data = after.data() ?? {};
  const estado = String(data.estado ?? "");
  if (!["EXITOSO", "PARCIAL", "FALLIDO"].includes(estado)) return;

  const eventId = `carga:${cargaId}:${estado}`;
  const title = estado === "FALLIDO" ? "Importación fallida" : "Importación completada";
  const body = estado === "FALLIDO"
    ? `No se pudo procesar ${String(data.nombreArchivo ?? "el archivo")}.`
    : `Se procesó ${String(data.nombreArchivo ?? "el archivo")} correctamente.`;

  await emitOnceAndPush(uid, eventId, {
    title,
    body,
    route: "historial",
    channelId: "push",
    notificationId: hashCode(eventId).toString(),
  });
});

export const onTransaccionCreated = onDocumentCreated("usuarios/{uid}/transacciones/{txId}", async (event) => {
  const uid = event.params.uid as string;
  const txId = event.params.txId as string;
  const snap = event.data;
  if (!snap?.exists) return;
  const tx = snap.data();
  if (!tx) return;
  if (tx.esDerivada) return;
  if (tx.tipo !== "DEBITO") return;
  if (!tx.categoriaId) return;

  const presupuestosSnap = await db.collection(`usuarios/${uid}/presupuestos`)
    .where("activo", "==", true)
    .where("categoriaId", "==", tx.categoriaId)
    .get();

  for (const presupuestoDoc of presupuestosSnap.docs) {
    const presupuesto = presupuestoDoc.data();
    const montoLimite = asNumber(presupuesto.montoLimite);
    if (!Number.isFinite(montoLimite) || montoLimite <= 0) continue;

    const { inicio, fin } = periodoPresupuesto(presupuesto.periodo);
    const gastos = await db.collection(`usuarios/${uid}/transacciones`)
      .where("tipo", "==", "DEBITO")
      .where("categoriaId", "==", tx.categoriaId)
      .where("fecha", ">=", inicio)
      .where("fecha", "<=", fin)
      .get();

    const total = gastos.docs.reduce((acc, doc) => acc + asNumber(doc.data().monto), 0);
    if (total <= montoLimite) continue;

    const eventId = `presupuesto:${presupuestoDoc.id}:${txId}`;
    await emitOnceAndPush(uid, eventId, {
      title: "Presupuesto excedido",
      body: `La categoría ${String(tx.categoriaId)} superó su presupuesto.`,
      route: "presupuestos",
      channelId: "push",
      notificationId: hashCode(eventId).toString(),
    });
  }
});

async function emitOnceAndPush(uid: string, eventId: string, payload: PushPayload): Promise<void> {
  const markerRef = db.doc(`usuarios/${uid}/notificacionesEmitidas/${eventId}`);
  const shouldSend = await db.runTransaction(async (trx) => {
    const marker = await trx.get(markerRef);
    if (marker.exists) return false;
    trx.set(markerRef, {
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      payload,
    });
    return true;
  });

  if (!shouldSend) return;

  const devicesSnap = await db.collection(`usuarios/${uid}/dispositivos`)
    .where("activo", "==", true)
    .get();

  const deviceTokens = devicesSnap.docs
    .map((doc) => ({
      doc,
      token: String(doc.data().tokenFcm ?? "").trim(),
    }))
    .filter((entry) => entry.token.length > 0);

  if (deviceTokens.length === 0) return;

  const message: admin.messaging.MulticastMessage = {
    tokens: deviceTokens.map((entry) => entry.token),
    data: {
      title: payload.title,
      body: payload.body,
      route: payload.route,
      channelId: payload.channelId,
      notificationId: payload.notificationId,
    },
  };

  const response = await admin.messaging().sendEachForMulticast(message);
  await cleanupInvalidTokens(uid, deviceTokens, response.responses);
}

async function cleanupInvalidTokens(
  uid: string,
  deviceTokens: Array<{ doc: admin.firestore.QueryDocumentSnapshot; token: string }>,
  responses: admin.messaging.BatchResponse["responses"],
): Promise<void> {
  const batch = db.batch();
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

function asNumber(value: unknown): number {
  if (typeof value === "number") return value;
  if (typeof value === "string") return Number.parseFloat(value);
  return Number.NaN;
}

function periodoPresupuesto(periodo: unknown): { inicio: Date; fin: Date } {
  const now = new Date();
  if (periodo === "ANUAL") {
    return {
      inicio: new Date(now.getFullYear(), 0, 1, 0, 0, 0, 0),
      fin: new Date(now.getFullYear(), 11, 31, 23, 59, 59, 999),
    };
  }

  return {
    inicio: new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0),
    fin: new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999),
  };
}

function hashCode(input: string): number {
  let hash = 0;
  for (let i = 0; i < input.length; i += 1) {
    hash = ((hash << 5) - hash) + input.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}
