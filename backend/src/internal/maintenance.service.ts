import { Injectable } from "@nestjs/common";
import { currentServiceMode, isModeAllowed } from "../common/service-mode";
import { GmailRepository } from "../integrations/gmail/gmail.repository";
import { GmailSyncService } from "../integrations/gmail/gmail-sync.service";

export interface MaintenanceSummary {
  renewedWatchCount: number;
  renewedWatches: Array<{ uid: string; watchExpirationAt: string }>;
}

@Injectable()
export class MaintenanceService {
  constructor(
    private readonly gmailRepository: GmailRepository,
    private readonly gmailSyncService: GmailSyncService,
  ) {}

  async renewDueWatches(): Promise<MaintenanceSummary> {
    this.assertWorkerMode();
    const due = await this.gmailRepository.listDueWatchIntegrations(new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString());
    const renewedWatches: Array<{ uid: string; watchExpirationAt: string }> = [];

    for (const item of due) {
      const result = await this.gmailSyncService.renewWatch(item.uid);
      renewedWatches.push({ uid: item.uid, watchExpirationAt: result.watchExpirationAt });
    }

    return {
      renewedWatchCount: renewedWatches.length,
      renewedWatches,
    };
  }

  private assertWorkerMode() {
    if (!isModeAllowed("worker")) {
      throw new Error(`Maintenance job unavailable in ${currentServiceMode()} mode.`);
    }
  }
}
