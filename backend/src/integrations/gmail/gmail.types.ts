export type GmailReadFilter = "read" | "unread" | "both";
export type GmailConnectionState = "idle" | "connected" | "syncing" | "error" | "disconnected";

export interface GmailDateRange {
  startDate?: string;
  endDate?: string;
}

export interface GmailIntegrationDoc {
  uidUsuario: string;
  estado: GmailConnectionState;
  readFilter: GmailReadFilter;
  dateRange: GmailDateRange | null;
  lastSyncAt: string | null;
  watchExpirationAt: string | null;
  connectedAt: string | null;
  disconnectedAt: string | null;
  updatedAt: string;
  createdAt: string;
}

export interface GmailOAuthTokensDoc {
  uidUsuario: string;
  accessToken: string | null;
  refreshToken: string | null;
  scope: string | null;
  tokenType: string | null;
  expiryDate: string | null;
  updatedAt: string;
  createdAt: string;
}

export interface GmailStatePayload {
  uid: string;
  nonce: string;
  readFilter: GmailReadFilter;
  dateRange: GmailDateRange | null;
  redirectPath: string;
  issuedAt: number;
  expiresAt: number;
}
