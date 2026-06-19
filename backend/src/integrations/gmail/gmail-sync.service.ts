import { BadRequestException, Injectable } from "@nestjs/common";
import { GmailRepository } from "./gmail.repository";
import { GmailApiError, GmailApiService } from "./gmail-api.service";
import { GmailIngestionService } from "./gmail-ingestion.service";
import { GmailDateRange, GmailReadFilter } from "./gmail.types";

export interface GmailSyncResult {
  uid: string;
  mode: "full" | "history" | "watch";
  query?: string;
  processedMessages: number;
  parsedTransactions: number;
  duplicateTransactions: number;
  ignoredMessages: number;
  checkpointHistoryId: string | null;
}

@Injectable()
export class GmailSyncService {
  constructor(
    private readonly gmailRepository: GmailRepository,
    private readonly gmailApi: GmailApiService,
    private readonly ingestionService: GmailIngestionService,
  ) {}

  async syncNow(
    uid: string,
    options?: { readFilter?: GmailReadFilter; dateRange?: GmailDateRange | null },
  ): Promise<GmailSyncResult> {
    const integration = await this.requireConnected(uid);
    const readFilter = options?.readFilter ?? integration.readFilter;
    const dateRange = options?.dateRange ?? integration.dateRange;
    const query = buildGmailQuery(readFilter, dateRange);

    await this.gmailRepository.setSyncing(uid);

    let processedMessages = 0;
    let parsedTransactions = 0;
    let duplicateTransactions = 0;
    let ignoredMessages = 0;
    let pageToken: string | undefined;

    do {
      const page = await this.gmailApi.listMessages(uid, query, pageToken);
      for (const message of page.messages ?? []) {
        if (!message.id) continue;
        const full = await this.gmailApi.getMessage(uid, message.id);
        const envelope = await this.gmailApi.buildEmailEnvelope(uid, full);
        const result = await this.ingestionService.ingest(envelope, "email_ingestion");
        processedMessages += 1;
        duplicateTransactions += result.duplicateCount ?? 0;
        if (result.accepted && (result.transactionIds?.length ?? 0) > 0) {
          parsedTransactions += result.transactionIds.length;
        } else {
          ignoredMessages += 1;
        }
      }
      pageToken = page.nextPageToken;
    } while (pageToken);

    const checkpointHistoryId = integration.lastHistoryId ?? null;
    await this.gmailRepository.markSyncCheckpoint(uid, new Date().toISOString(), checkpointHistoryId);

    return {
      uid,
      mode: "full",
      query,
      processedMessages,
      parsedTransactions,
      duplicateTransactions,
      ignoredMessages,
      checkpointHistoryId,
    };
  }

  async renewWatch(uid: string): Promise<GmailSyncResult & { watchExpirationAt: string }> {
    await this.requireConnected(uid);
    const response = await this.gmailApi.watch(uid);
    const watchExpirationAt = response.expiration ? new Date(Number(response.expiration)).toISOString() : new Date().toISOString();
    const lastHistoryId = response.historyId ?? null;
    await this.gmailRepository.markWatch(uid, watchExpirationAt, lastHistoryId);

    return {
      uid,
      mode: "watch",
      processedMessages: 0,
      parsedTransactions: 0,
      duplicateTransactions: 0,
      ignoredMessages: 0,
      checkpointHistoryId: lastHistoryId,
      watchExpirationAt,
    };
  }

  async syncFromHistory(uid: string, startHistoryId?: string | null): Promise<GmailSyncResult> {
    const integration = await this.requireConnected(uid);
    const checkpoint = startHistoryId ?? integration.lastHistoryId;
    if (!checkpoint) {
      return this.syncNow(uid, { readFilter: integration.readFilter, dateRange: integration.dateRange });
    }

    await this.gmailRepository.setSyncing(uid);

    let processedMessages = 0;
    let parsedTransactions = 0;
    let duplicateTransactions = 0;
    let ignoredMessages = 0;
    let pageToken: string | undefined;
    let latestHistoryId: string | null = checkpoint;

    try {
      do {
        const page = await this.gmailApi.listHistory(uid, checkpoint, pageToken);
        latestHistoryId = page.historyId ?? latestHistoryId;

        for (const history of page.history ?? []) {
          for (const messageAdded of history.messagesAdded ?? []) {
            const messageId = messageAdded.message?.id;
            if (!messageId) continue;
            const full = await this.gmailApi.getMessage(uid, messageId);
            const envelope = await this.gmailApi.buildEmailEnvelope(uid, full);
            const result = await this.ingestionService.ingest(envelope, "email_ingestion");
            processedMessages += 1;
            duplicateTransactions += result.duplicateCount ?? 0;
            if (result.accepted && (result.transactionIds?.length ?? 0) > 0) {
              parsedTransactions += result.transactionIds.length;
            } else {
              ignoredMessages += 1;
            }
          }
        }

        pageToken = page.nextPageToken;
      } while (pageToken);
    } catch (error) {
      if (error instanceof GmailApiError && error.status === 404) {
        return this.syncNow(uid, { readFilter: integration.readFilter, dateRange: integration.dateRange });
      }
      throw error;
    }

    await this.gmailRepository.markSyncCheckpoint(uid, new Date().toISOString(), latestHistoryId);

    return {
      uid,
      mode: "history",
      processedMessages,
      parsedTransactions,
      duplicateTransactions,
      ignoredMessages,
      checkpointHistoryId: latestHistoryId,
    };
  }

  private async requireConnected(uid: string) {
    const integration = await this.gmailRepository.getIntegration(uid);
    if (!integration) {
      throw new BadRequestException("Gmail integration is not configured for this user.");
    }
    if (integration.estado === "disconnected") {
      throw new BadRequestException("Gmail integration is disconnected.");
    }
    return integration;
  }
}

function buildGmailQuery(readFilter: GmailReadFilter, dateRange: GmailDateRange | null): string {
  const terms: string[] = [];
  if (readFilter === "read") terms.push("is:read");
  if (readFilter === "unread") terms.push("is:unread");
  if (dateRange?.startDate) terms.push(`after:${normalizeDateForQuery(dateRange.startDate)}`);
  if (dateRange?.endDate) terms.push(`before:${normalizeEndDateForQuery(dateRange.endDate)}`);
  return terms.join(" ");
}

function normalizeDateForQuery(value: string): string {
  const trimmed = value.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    const [year, month, day] = trimmed.split("-");
    return `${year}/${month}/${day}`;
  }
  return trimmed;
}

function normalizeEndDateForQuery(value: string): string {
  const normalized = normalizeDateForQuery(value);
  if (/^\d{4}\/\d{2}\/\d{2}$/.test(normalized)) {
    const [year, month, day] = normalized.split("/");
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    date.setUTCDate(date.getUTCDate() + 1);
    return `${date.getUTCFullYear()}/${String(date.getUTCMonth() + 1).padStart(2, "0")}/${String(date.getUTCDate()).padStart(2, "0")}`;
  }
  return normalized;
}
