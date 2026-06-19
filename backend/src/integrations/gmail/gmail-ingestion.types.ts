export type GmailIngestionChannel = "pubsub" | "email_ingestion";
export type GmailParsedProductType = "CUENTA" | "TARJETA";
export type GmailParsedEstado = "APROBADA" | "RECHAZADA" | "DUPLICADA" | "PENDIENTE";
export type GmailParsedTipo = "DEBITO" | "CREDITO";
export type GmailParsedMoneda = "DOP" | "USD";

export interface GmailIngestionEnvelope {
  uidUsuario?: string;
  uid?: string;
  sourceEventId?: string;
  sourceMessageId?: string;
  sourceTransactionId?: string;
  bankCode?: string;
  from?: string;
  subject?: string;
  snippet?: string;
  body?: string;
  read?: boolean;
  labels?: string[];
  receivedAt?: string;
  sentAt?: string;
  threadId?: string;
  historyId?: string;
  channel?: GmailIngestionChannel;
  payload?: unknown;
}

export interface GmailParserAccountMatch {
  productType: GmailParsedProductType;
  cuentaId: string;
  tarjetaId: string | null;
  numeroCuenta: string | null;
  numeroCuentaCompleto: string | null;
  ultimos4: string | null;
  alias: string;
  titular: string;
  moneda: GmailParsedMoneda;
  tipoCuenta: string;
  tipoRed: string | null;
}

export interface GmailParsedTransactionDraft {
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
  serial: string | null;
  categoriaId: string | null;
  categoriaAutomatica: boolean;
  esDerivada: boolean;
  transaccionPadreId: string | null;
  derivadasIds: string[];
  origen: "INGESTA_GMAIL";
  estado: GmailParsedEstado;
  afectaBalance: boolean;
  posibleDuplicado: boolean;
  motivoRechazo: string | null;
  cargaId: string;
  notaUsuario: string | null;
  metadataBanco: Record<string, string>;
  creadoEn: Date;
  actualizadoEn: Date;
}

export interface GmailParsedEmailResult {
  parserId: string;
  parserVersion: number;
  bankCode: string;
  confidence: number;
  account: GmailParserAccountMatch;
  transactions: GmailParsedTransactionDraft[];
  warnings: string[];
  signals: Record<string, string>;
}

