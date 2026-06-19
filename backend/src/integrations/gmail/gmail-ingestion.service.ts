import { BadRequestException, Injectable, Logger } from "@nestjs/common";
import { Timestamp } from "firebase-admin/firestore";
import { GmailRepository } from "./gmail.repository";
import { GmailIngestionEnvelope, GmailParsedEmailResult } from "./gmail-ingestion.types";
import { parseEmailEnvelope } from "./gmail-email-parser";
import { createHash } from "crypto";

@Injectable()
export class GmailIngestionService {
  private readonly logger = new Logger(GmailIngestionService.name);

  constructor(private readonly gmailRepository: GmailRepository) {}

  async ingest(payload: unknown, channel: "email_ingestion" | "pubsub") {
    const envelope = normalizeEnvelope(payload, channel);
    const uid = envelope.uidUsuario ?? envelope.uid ?? null;
    const sourceEventId = this.resolveSourceEventId(envelope);

    if (!uid && channel === "email_ingestion") {
      throw new BadRequestException("Missing uidUsuario in email ingestion payload.");
    }

    const existingEvent = await this.gmailRepository.getEvent(sourceEventId);
    if (existingEvent?.status === "processed" && Array.isArray(existingEvent.transactionIds)) {
      return {
        accepted: true,
        replay: true,
        sourceEventId,
        uid,
        transactionIds: existingEvent.transactionIds as string[],
        parserId: existingEvent.parserId ?? null,
        warnings: existingEvent.warnings ?? [],
      };
    }

    const parsed = parseEmailEnvelope(envelope, sourceEventId);
    if (!parsed) {
      await this.gmailRepository.recordEvent(sourceEventId, {
        kind: channel,
        status: "ignored",
        uidUsuario: uid,
        payload: envelope.payload ?? envelope,
        warnings: ["No se encontro un parser compatible."],
      });

      return {
        accepted: false,
        sourceEventId,
        uid,
        transactionIds: [],
        parserId: null,
        warnings: ["No se encontro un parser compatible."],
      };
    }

    if (!uid) {
      throw new BadRequestException("Missing uidUsuario in parsed Gmail payload.");
    }

    const accountId = parsed.account.productType === "TARJETA" ? parsed.account.tarjetaId ?? parsed.account.cuentaId : parsed.account.cuentaId;
    const similarTransactions = await this.gmailRepository.listRecentTransactions(uid, 200);
    const transactionIds: string[] = [];
    const processedTransactions = [];

    if (parsed.account.productType === "TARJETA") {
      await this.gmailRepository.upsertCard(uid, parsed.account.tarjetaId ?? parsed.account.cuentaId, {
        bancoCodigo: parsed.bankCode,
        ultimos4: parsed.account.ultimos4 ?? "0000",
        alias: parsed.account.alias,
        tipoRed: parsed.account.tipoRed,
        limiteCredito: "0.00",
        moneda: parsed.account.moneda,
        diaCorte: 1,
        diaPago: 1,
        tasaInteresAnual: "0.00",
        tasaInteresOrigen: "MANUAL",
        estado: "ACTIVO",
        titular: parsed.account.titular,
        activa: true,
        ultimaSincronizacion: Timestamp.now(),
      });
    } else {
      await this.gmailRepository.upsertAccount(uid, parsed.account.cuentaId, {
        bancoCodigo: parsed.bankCode,
        numeroCuenta: parsed.account.numeroCuenta ?? "000000",
        numeroCuentaCompleto: parsed.account.numeroCuentaCompleto,
        alias: parsed.account.alias,
        tipoCuenta: parsed.account.tipoCuenta,
        moneda: parsed.account.moneda,
        balanceActual: parsed.transactions[0]?.balanceDespues,
        balanceAlCorte: parsed.transactions[0]?.balanceDespues,
        fechaUltimoCorte: parsed.transactions[0]?.fecha ? Timestamp.fromDate(parsed.transactions[0].fecha) : null,
        titular: parsed.account.titular,
        activa: true,
        mostrarEnDashboard: true,
        ultimaSincronizacion: Timestamp.now(),
      });
    }

    for (const [index, transaction] of parsed.transactions.entries()) {
      const transactionId = this.buildTransactionId({
        uid,
        accountId,
        sourceEventId,
        sourceMessageId: transaction.sourceMessageId ?? envelope.sourceMessageId ?? null,
        sourceTransactionId: transaction.sourceTransactionId,
        index,
        fecha: transaction.fecha,
        monto: transaction.monto,
        tipo: transaction.tipo,
        descripcionNormalizada: transaction.descripcionNormalizada,
      });

      const duplicateMatch = findSimilarTransaction(similarTransactions, {
        accountId,
        fecha: transaction.fecha,
        monto: transaction.monto,
        tipo: transaction.tipo,
        descripcionNormalizada: transaction.descripcionNormalizada,
      });

      const doc = {
        id: transactionId,
        uidUsuario: uid,
        cuentaId: accountId,
        bancoCodigo: parsed.bankCode,
        fecha: Timestamp.fromDate(transaction.fecha),
        fechaPosteo: transaction.fechaPosteo ? Timestamp.fromDate(transaction.fechaPosteo) : null,
        descripcionCorta: transaction.descripcionCorta,
        descripcionOriginal: transaction.descripcionOriginal,
        descripcionNormalizada: transaction.descripcionNormalizada,
        monto: transaction.monto,
        tipo: transaction.tipo,
        moneda: transaction.moneda,
        balanceDespues: transaction.balanceDespues,
        referencia: transaction.referencia,
        serial: transaction.serial,
        categoriaId: transaction.categoriaId,
        categoriaAutomatica: transaction.categoriaAutomatica,
        esDerivada: transaction.esDerivada,
        transaccionPadreId: transaction.transaccionPadreId,
        derivadasIds: transaction.derivadasIds,
        origen: "INGESTA_GMAIL",
        sourceEventId,
        sourceMessageId: transaction.sourceMessageId ?? envelope.sourceMessageId ?? null,
        sourceTransactionId: transaction.sourceTransactionId,
        actualizadoEn: Timestamp.now(),
        estado: duplicateMatch ? "DUPLICADA" : transaction.estado,
        afectaBalance: duplicateMatch ? false : transaction.afectaBalance,
        posibleDuplicado: Boolean(duplicateMatch),
        motivoRechazo: transaction.motivoRechazo,
        cargaId: transaction.cargaId,
        notaUsuario: transaction.notaUsuario,
        metadataBanco: {
          ...transaction.metadataBanco,
          parserId: parsed.parserId,
          parserVersion: String(parsed.parserVersion),
          sourceChannel: channel,
          sourceEventId,
          sourceMessageId: transaction.sourceMessageId ?? envelope.sourceMessageId ?? "",
          duplicateOfId: asString(duplicateMatch?.id) ?? "",
        },
        creadoEn: Timestamp.now(),
      };

      await this.gmailRepository.upsertTransaction(uid, transactionId, doc);
      transactionIds.push(transactionId);
      processedTransactions.push({ transactionId, duplicateOfId: duplicateMatch?.id ?? null });
    }

    await this.gmailRepository.recordEvent(sourceEventId, {
      kind: channel,
      status: "processed",
      uidUsuario: uid,
      parserId: parsed.parserId,
      parserVersion: parsed.parserVersion,
      transactionIds,
      warnings: parsed.warnings,
      accountId,
      payload: envelope.payload ?? envelope,
      processedTransactions,
    });

    return {
      accepted: true,
      sourceEventId,
      uid,
      parserId: parsed.parserId,
      transactionIds,
      warnings: parsed.warnings,
      duplicateCount: processedTransactions.filter((item) => item.duplicateOfId).length,
    };
  }

  private resolveSourceEventId(envelope: GmailIngestionEnvelope): string {
    const explicit = envelope.sourceEventId ?? envelope.sourceMessageId ?? envelope.sourceTransactionId;
    if (explicit) return explicit;
    const input = JSON.stringify({
      uid: envelope.uidUsuario ?? envelope.uid ?? "",
      subject: envelope.subject ?? "",
      from: envelope.from ?? "",
      body: envelope.body ?? envelope.snippet ?? "",
      receivedAt: envelope.receivedAt ?? "",
      sentAt: envelope.sentAt ?? "",
    });
    return createHash("sha256").update(input, "utf8").digest("hex").slice(0, 20);
  }

  private buildTransactionId(args: {
    uid: string;
    accountId: string;
    sourceEventId: string;
    sourceMessageId: string | null;
    sourceTransactionId: string | null;
    index: number;
    fecha: Date;
    monto: string;
    tipo: string;
    descripcionNormalizada: string;
  }) {
    return createHash("sha256")
      .update(
        [
          args.uid,
          args.accountId,
          args.sourceEventId,
          args.sourceMessageId ?? "",
          args.sourceTransactionId ?? "",
          String(args.index),
          String(args.fecha.getTime()),
          args.monto,
          args.tipo,
          args.descripcionNormalizada,
        ].join("|"),
        "utf8",
      )
      .digest("hex")
      .slice(0, 20);
  }
}

function normalizeEnvelope(payload: unknown, channel: "email_ingestion" | "pubsub"): GmailIngestionEnvelope {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return { channel };
  }

  const record = payload as Record<string, unknown>;
  const nested = maybeDecodePubSub(record);
  const source = nested ?? record;

  return {
    uidUsuario: asString(source.uidUsuario) ?? asString(source.uid),
    uid: asString(source.uid),
    sourceEventId: asString(source.sourceEventId),
    sourceMessageId: asString(source.sourceMessageId) ?? asString(source.messageId),
    sourceTransactionId: asString(source.sourceTransactionId),
    bankCode: asString(source.bankCode),
    from: asString(source.from),
    subject: asString(source.subject),
    snippet: asString(source.snippet),
    body: asString(source.body) ?? asString(source.text),
    read: asBoolean(source.read),
    labels: Array.isArray(source.labels) ? source.labels.filter((item): item is string => typeof item === "string") : undefined,
    receivedAt: asString(source.receivedAt),
    sentAt: asString(source.sentAt),
    threadId: asString(source.threadId),
    historyId: asString(source.historyId),
    channel,
    payload: source,
  };
}

function maybeDecodePubSub(record: Record<string, unknown>) {
  const message = record.message;
  if (!message || typeof message !== "object" || Array.isArray(message)) {
    return null;
  }

  const messageRecord = message as Record<string, unknown>;
  const data = messageRecord.data;
  if (typeof data !== "string" || !data.trim()) {
    return null;
  }

  try {
    const normalized = data.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const decoded = Buffer.from(padded, "base64").toString("utf8");
    const parsed = JSON.parse(decoded);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
  } catch {
    return null;
  }
  return null;
}

function findSimilarTransaction(
  transactions: Record<string, unknown>[],
  args: {
    accountId: string;
    fecha: Date;
    monto: string;
    tipo: string;
    descripcionNormalizada: string;
  },
) {
  const targetDate = args.fecha.toISOString().slice(0, 10);
  return transactions.find((tx) => {
    const cuentaId = asString(tx.cuentaId);
    if (cuentaId !== args.accountId) return false;
    const monto = asString(tx.monto);
    const tipo = asString(tx.tipo);
    const descripcion = asString(tx.descripcionNormalizada);
    const fecha = asTimestamp(tx.fecha);
    const fechaStr = fecha ? fecha.toDate().toISOString().slice(0, 10) : null;
    return Boolean(
      monto === args.monto &&
      tipo === args.tipo &&
      descripcion === args.descripcionNormalizada &&
      (fechaStr === targetDate || fechaStr === shiftDate(targetDate, -1) || fechaStr === shiftDate(targetDate, 1)),
    );
  }) ?? null;
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function asBoolean(value: unknown): boolean | undefined {
  return typeof value === "boolean" ? value : undefined;
}

function asTimestamp(value: unknown): Timestamp | null {
  return value instanceof Timestamp ? value : null;
}

function shiftDate(date: string, delta: number) {
  const parsed = new Date(`${date}T00:00:00.000Z`);
  parsed.setUTCDate(parsed.getUTCDate() + delta);
  return parsed.toISOString().slice(0, 10);
}
