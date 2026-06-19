import { Injectable } from "@nestjs/common";
import { NotificationRepository } from "./notification.repository";

@Injectable()
export class NotificationService {
  constructor(private readonly notifications: NotificationRepository) {}

  async handleCargaWritten(payload: unknown) {
    const event = normalizeRecord(payload);
    const uid = readString(event.uid);
    const cargaId = readString(event.cargaId);
    const after = normalizeRecord(event.after);
    const exists = asBoolean(after.exists);
    const data = normalizeRecord(after.data);
    const estado = readString(data.estado) ?? readString(event.estado);
    const nombreArchivo = readString(data.nombreArchivo) ?? "el archivo";

    if (!uid || !cargaId || !exists || !estado) {
      return { accepted: false, reason: "missing_data" };
    }

    if (!["EXITOSO", "PARCIAL", "FALLIDO"].includes(estado)) {
      return { accepted: false, reason: "ignored_state", estado };
    }

    const eventId = `carga:${cargaId}:${estado}`;
    const title = estado === "FALLIDO" ? "Importacion fallida" : "Importacion completada";
    const body = estado === "FALLIDO"
      ? `No se pudo procesar ${nombreArchivo}.`
      : `Se proceso ${nombreArchivo} correctamente.`;

    const sent = await this.notifications.emitOnceAndPush(uid, eventId, {
      title,
      body,
      route: "historial",
      channelId: "push",
      notificationId: hashCode(eventId).toString(),
    });

    return { accepted: true, uid, cargaId, estado, sent, eventId };
  }

  async handleTransaccionCreated(payload: unknown) {
    const tx = normalizeRecord(payload);
    const uid = readString(tx.uid) ?? readString(tx.uidUsuario);
    const txId = readString(tx.id) ?? readString(tx.txId);
    if (!uid || !txId) {
      return { accepted: false, reason: "missing_data" };
    }

    if (asBoolean(tx.esDerivada)) {
      return { accepted: false, reason: "derived_transaction" };
    }
    if (readString(tx.tipo) !== "DEBITO") {
      return { accepted: false, reason: "non_debit" };
    }
    const categoriaId = readString(tx.categoriaId);
    if (!categoriaId) {
      return { accepted: false, reason: "no_category" };
    }

    const presupuestos = await this.notifications.listActiveBudgetRules(uid, categoriaId);
    const triggered: string[] = [];
    for (const presupuesto of presupuestos) {
      const montoLimite = asNumber(presupuesto.montoLimite);
      if (!Number.isFinite(montoLimite) || montoLimite <= 0) continue;

      const { inicio, fin } = periodoPresupuesto(readString(presupuesto.periodo));
      const total = await this.notifications.sumCategoryDebit(uid, categoriaId, inicio, fin);
      if (total <= montoLimite) continue;

      const eventId = `presupuesto:${String(presupuesto.id ?? categoriaId)}:${txId}`;
      const sent = await this.notifications.emitOnceAndPush(uid, eventId, {
        title: "Presupuesto excedido",
        body: `La categoria ${categoriaId} supero su presupuesto.`,
        route: "presupuestos",
        channelId: "push",
        notificationId: hashCode(eventId).toString(),
      });
      if (sent) triggered.push(eventId);
    }

    return { accepted: true, uid, txId, triggered };
  }
}

function normalizeRecord(value: unknown): Record<string, unknown> {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return {};
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function asBoolean(value: unknown): boolean {
  return value === true;
}

function asNumber(value: unknown): number {
  if (typeof value === "number") return value;
  if (typeof value === "string") return Number.parseFloat(value);
  return Number.NaN;
}

function periodoPresupuesto(periodo: string | null): { inicio: Date; fin: Date } {
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
