import { createHash } from "crypto";
import { GmailIngestionEnvelope, GmailParsedEmailResult, GmailParsedMoneda, GmailParsedTipo, GmailParserAccountMatch, GmailParsedTransactionDraft } from "./gmail-ingestion.types";

type ParserFn = (envelope: GmailIngestionEnvelope, sourceEventId: string) => GmailParsedEmailResult | null;

const BANRESERVAS_PATTERNS = [
  /banreservas/i,
  /banca(?:\s+|-)en(?:\s+|-)linea/i,
  /confirmaci[oó]n/i,
];

export function parseEmailEnvelope(envelope: GmailIngestionEnvelope, sourceEventId: string): GmailParsedEmailResult | null {
  const parsers: Array<{ id: string; version: number; parse: ParserFn }> = [
    { id: "BANRESERVAS_EMAIL_v1", version: 1, parse: parseBanreservasEmail },
  ];

  for (const parser of parsers) {
    const result = parser.parse(envelope, sourceEventId);
    if (result) {
      return {
        ...result,
        parserId: parser.id,
        parserVersion: parser.version,
      };
    }
  }
  return null;
}

function parseBanreservasEmail(envelope: GmailIngestionEnvelope, sourceEventId: string): GmailParsedEmailResult | null {
  const rawText = collectText(envelope);
  const subject = normalizeText(envelope.subject);
  const from = normalizeText(envelope.from);
  const combined = normalizeText([subject, from, rawText].filter(Boolean).join("\n"));

  if (!BANRESERVAS_PATTERNS.some((pattern) => pattern.test(combined))) {
    return null;
  }

  const accountMatch = extractAccountMatch(envelope, combined);
  const fecha = extractDate(envelope, combined);
  const amounts = extractAmounts(combined);
  const sourceMessageId = cleanString(envelope.sourceMessageId);
  const sourceTransactionId = extractReference(combined);
  const rechazado = /(rechazad[oa]|declinad[oa]|denegad[oa]|no aprob)/i.test(combined);
  const credito = /(deposito|dep[oó]sito|abono|reembolso|cashback|devoluci[oó]n|transferencia recibida|pago recibido)/i.test(combined);
  const tipo: GmailParsedTipo = rechazado ? "DEBITO" : credito ? "CREDITO" : "DEBITO";
  const moneda: GmailParsedMoneda = /(us\$|usd|d[oó]lares?)/i.test(combined) ? "USD" : "DOP";
  const monto = amounts[0] ?? "0.00";
  const balanceDespues = amounts.length > 1 ? amounts[amounts.length - 1] : null;
  const descripcionOriginal = buildDescripcion(subject, rawText);
  const descripcionCorta = shortenDescripcion(descripcionOriginal);
  const descripcionNormalizada = normalizeSignature(descripcionOriginal);
  const fechaPosteo = extractDateTime(envelope, combined);
  const motivoRechazo = rechazado ? extractRejectionReason(combined) : null;

  const transaction = buildTransactionDraft({
    sourceEventId,
    sourceMessageId,
    sourceTransactionId,
    fecha,
    fechaPosteo,
    descripcionCorta,
    descripcionOriginal,
    descripcionNormalizada,
    monto,
    tipo,
    moneda,
    balanceDespues,
    referencia: sourceTransactionId,
    motivoRechazo,
    account: accountMatch,
  });

  return {
    parserId: "BANRESERVAS_EMAIL_v1",
    parserVersion: 1,
    bankCode: "BANRESERVAS",
    confidence: 0.9,
    account: accountMatch,
    transactions: [transaction],
    warnings: rejectedWarnings(rechazado, accountMatch, fecha, amounts),
    signals: {
      sourceEventId,
      sourceMessageId: sourceMessageId ?? "",
      sourceTransactionId: sourceTransactionId ?? "",
      hasRawBody: rawText ? "true" : "false",
    },
  };
}

function buildTransactionDraft(args: {
  sourceEventId: string;
  sourceMessageId: string | null;
  sourceTransactionId: string | null;
  fecha: Date;
  fechaPosteo: Date | null;
  descripcionCorta: string;
  descripcionOriginal: string;
  descripcionNormalizada: string;
  monto: string;
  tipo: GmailParsedTipo;
  moneda: GmailParsedMoneda;
  balanceDespues: string | null;
  referencia: string | null;
  motivoRechazo: string | null;
  account: GmailParserAccountMatch;
}): GmailParsedTransactionDraft {
  const ahora = new Date();
  const afectABalance = args.tipo === "DEBITO" && !args.motivoRechazo;
  return {
    sourceMessageId: args.sourceMessageId,
    sourceTransactionId: args.sourceTransactionId,
    fecha: args.fecha,
    fechaPosteo: args.fechaPosteo,
    descripcionCorta: args.descripcionCorta,
    descripcionOriginal: args.descripcionOriginal,
    descripcionNormalizada: args.descripcionNormalizada,
    monto: args.monto,
    tipo: args.tipo,
    moneda: args.moneda,
    balanceDespues: args.balanceDespues,
    referencia: args.referencia,
    serial: null,
    categoriaId: null,
    categoriaAutomatica: false,
    esDerivada: false,
    transaccionPadreId: null,
    derivadasIds: [],
    origen: "INGESTA_GMAIL",
    estado: args.motivoRechazo ? "RECHAZADA" : "APROBADA",
    afectaBalance: afectABalance,
    posibleDuplicado: false,
    motivoRechazo: args.motivoRechazo,
    cargaId: `gmail:${args.sourceEventId}`,
    notaUsuario: null,
    metadataBanco: {
      bankCode: "BANRESERVAS",
      productType: args.account.productType,
      productId: args.account.numeroCuenta ?? args.account.ultimos4 ?? "",
    },
    creadoEn: ahora,
    actualizadoEn: ahora,
  };
}

function rejectedWarnings(rechazado: boolean, account: GmailParserAccountMatch, fecha: Date, amounts: string[]) {
  const warnings: string[] = [];
  if (rechazado) warnings.push("La transaccion fue rechazada y se marcara sin afectar balance.");
  if (!account.numeroCuenta && !account.ultimos4) warnings.push("No se pudo extraer un identificador de cuenta o tarjeta.");
  if (Number.isNaN(fecha.getTime())) warnings.push("No se pudo extraer una fecha valida.");
  if (amounts.length === 0) warnings.push("No se detecto un monto claro en el correo.");
  return warnings;
}

function collectText(envelope: GmailIngestionEnvelope): string {
  const pieces: Array<string | null | undefined> = [envelope.body, envelope.snippet];
  if (envelope.payload && typeof envelope.payload === "object" && !Array.isArray(envelope.payload)) {
    const record = envelope.payload as Record<string, unknown>;
    pieces.push(asString(record.body));
    pieces.push(asString(record.snippet));
    pieces.push(asString(record.text));
    pieces.push(asString(record.message));
  }
  return pieces.filter((value): value is string => Boolean(value && value.trim())).join("\n");
}

function extractAccountMatch(envelope: GmailIngestionEnvelope, combined: string): GmailParserAccountMatch {
  const card = combined.match(/(?:terminad[oa]\s+en|finalizad[oa]\s+en|\*{2,}|\bcard(?:\s+ending)?\s+in)\s*(\d{4})/i)?.[1]
    ?? combined.match(/\b(\d{4})\b(?!.*\b\d{4}\b)/)?.[1]
    ?? null;
  const number = combined.match(/(?:cuenta(?:\s+de)?(?:\s+ahorro|\s+corriente)?|no\.?\s*cuenta|n[uú]mero de cuenta)\D{0,20}(\d[\d\s-]{5,})/i)?.[1]
    ?? null;
  const cleanNumber = cleanDigits(number);
  const cleanCard = card?.trim() ?? null;
  const numeroCuentaCompleto = combined.match(/DO\d{2}BRRD\d+/i)?.[0] ?? null;
  const uid = cleanString(envelope.uidUsuario ?? envelope.uid) ?? "unknown";
  const bankCode = "BANRESERVAS";

  if (cleanCard) {
    const tarjetaId = hashId(uid, bankCode, cleanCard);
    return {
      productType: "TARJETA",
      cuentaId: tarjetaId,
      tarjetaId,
      numeroCuenta: null,
      numeroCuentaCompleto,
      ultimos4: cleanCard,
      alias: `BanReservas ****${cleanCard}`,
      titular: extractTitular(combined),
      moneda: "DOP",
      tipoCuenta: "CREDITO",
      tipoRed: extractRed(combined),
    };
  }

  const accountNumber = (cleanNumber || numeroCuentaCompleto?.slice(-10) || "000000").slice(-10);
  const cuentaId = hashId(uid, bankCode, accountNumber);
  return {
    productType: "CUENTA",
    cuentaId,
    tarjetaId: null,
    numeroCuenta: accountNumber,
    numeroCuentaCompleto,
    ultimos4: null,
    alias: `BanReservas ${accountNumber}`,
    titular: extractTitular(combined),
    moneda: "DOP",
    tipoCuenta: "CORRIENTE",
    tipoRed: null,
  };
}

function extractTitular(text: string): string {
  const match = text.match(/(?:titular|nombre(?: del titular)?|a nombre de)\D{0,20}([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]{3,60})/i)?.[1];
  return match ? normalizeWhitespace(match).slice(0, 60) : "TITULAR";
}

function extractRed(text: string): string | null {
  if (/mastercard/i.test(text)) return "MASTERCARD";
  if (/visa/i.test(text)) return "VISA";
  return null;
}

function extractDate(envelope: GmailIngestionEnvelope, combined: string): Date {
  const explicit = envelope.receivedAt ?? envelope.sentAt;
  if (explicit) {
    const parsed = new Date(explicit);
    if (!Number.isNaN(parsed.getTime())) return parsed;
  }

  const match = combined.match(/\b(\d{1,2}\/\d{1,2}\/\d{2,4})\b/);
  if (match) {
    const parts = match[1].split("/");
    const day = Number(parts[0]);
    const month = Number(parts[1]) - 1;
    const year = normalizeYear(Number(parts[2]));
    const parsed = new Date(Date.UTC(year, month, day));
    if (!Number.isNaN(parsed.getTime())) return parsed;
  }

  return new Date();
}

function extractDateTime(envelope: GmailIngestionEnvelope, combined: string): Date | null {
  const date = extractDate(envelope, combined);
  return Number.isNaN(date.getTime()) ? null : date;
}

function extractAmounts(text: string): string[] {
  const matches = [...text.matchAll(/(?:RD\$|US\$|\$)?\s*(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})|\d+(?:[.,]\d{2}))/gi)];
  const amounts = matches
    .map((match) => normalizeMoney(match[1]))
    .filter((value): value is string => Boolean(value));
  return [...new Set(amounts)];
}

function extractReference(text: string): string | null {
  const match = text.match(/(?:referencia|ref(?:erencia)?|autorizaci[oó]n|txn|transacci[oó]n)\D{0,20}([A-Z0-9-]{4,})/i)?.[1];
  return cleanString(match);
}

function extractRejectionReason(text: string): string {
  const match = text.match(/(?:rechazad[oa]|declinad[oa]|denegad[oa])[^\.\n]{0,120}/i)?.[0];
  return normalizeWhitespace(match ?? "Transaccion rechazada");
}

function buildDescripcion(subject: string, body: string): string {
  const base = [subject, body.split("\n").slice(0, 8).join(" ")].filter(Boolean).join(" - ");
  return normalizeWhitespace(base).slice(0, 160);
}

function shortenDescripcion(description: string): string {
  return normalizeWhitespace(description).slice(0, 60);
}

function normalizeText(value?: string | null): string {
  return normalizeWhitespace(value ?? "").toLowerCase();
}

function normalizeWhitespace(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

function normalizeSignature(value: string): string {
  return normalizeWhitespace(
    value
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^a-zA-Z0-9\s.-]/g, " ")
      .toUpperCase(),
  );
}

function normalizeMoney(value: string): string | null {
  const cleaned = value.replace(/\s+/g, "").replace(/[^\d,.-]/g, "");
  if (!cleaned) return null;
  const hasComma = cleaned.includes(",");
  const hasDot = cleaned.includes(".");
  let normalized = cleaned;
  if (hasComma && hasDot) {
    if (cleaned.lastIndexOf(",") > cleaned.lastIndexOf(".")) {
      normalized = cleaned.replace(/\./g, "").replace(",", ".");
    } else {
      normalized = cleaned.replace(/,/g, "");
    }
  } else if (hasComma) {
    const parts = cleaned.split(",");
    normalized = parts.length === 2 && parts[1].length <= 2 ? cleaned.replace(",", ".") : cleaned.replace(/,/g, "");
  }
  const parsed = Number.parseFloat(normalized);
  if (Number.isNaN(parsed)) return null;
  return parsed.toFixed(2);
}

function cleanDigits(value: string | null): string | null {
  if (!value) return null;
  const digits = value.replace(/\D/g, "");
  return digits ? digits.slice(-10) : null;
}

function cleanString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function normalizeYear(value: number): number {
  if (value < 100) {
    return value >= 70 ? 1900 + value : 2000 + value;
  }
  return value;
}

function hashId(...parts: Array<string | null | undefined>) {
  return createHash("sha256")
    .update(parts.map((part) => part?.trim() || "").join("|"), "utf8")
    .digest("hex")
    .slice(0, 20);
}
