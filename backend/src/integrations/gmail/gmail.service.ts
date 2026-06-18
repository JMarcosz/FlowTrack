import { Injectable } from "@nestjs/common";

export type GmailSyncState = "idle" | "connected" | "syncing" | "error";

@Injectable()
export class GmailService {
  startConnect(uid: string) {
    return {
      action: "start_connect",
      uid,
      authorizationUrl: null,
      stateToken: null,
    };
  }

  handleCallback(query: Record<string, string | undefined>) {
    return {
      action: "oauth_callback",
      codePresent: Boolean(query.code),
      statePresent: Boolean(query.state),
    };
  }

  getStatus(uid: string) {
    return {
      uid,
      state: "idle" as GmailSyncState,
      lastSyncAt: null,
      readFilter: "both",
      dateRange: null,
    };
  }

  disconnect(uid: string) {
    return { action: "disconnect", uid, disconnected: true };
  }

  requestSync(uid: string) {
    return {
      action: "sync_requested",
      uid,
      queued: true,
      source: "android",
    };
  }

  handlePubSubGmail(payload: unknown) {
    return { action: "pubsub_gmail", accepted: true, payload };
  }

  handleEmailIngestion(payload: unknown) {
    return { action: "email_ingestion", accepted: true, payload };
  }

  renewWatch(payload: unknown) {
    return { action: "watch_renew", accepted: true, payload };
  }
}
