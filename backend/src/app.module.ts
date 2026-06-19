import { Module } from "@nestjs/common";
import { ConfigModule } from "@nestjs/config";
import { RequestAuthService } from "./common/auth.service";
import { HealthController } from "./health/health.controller";
import { GmailController } from "./integrations/gmail/gmail.controller";
import { GmailApiService } from "./integrations/gmail/gmail-api.service";
import { GmailSyncService } from "./integrations/gmail/gmail-sync.service";
import { GmailRepository } from "./integrations/gmail/gmail.repository";
import { GmailIngestionService } from "./integrations/gmail/gmail-ingestion.service";
import { GmailStateService } from "./integrations/gmail/gmail-state.service";
import { GmailService } from "./integrations/gmail/gmail.service";
import { InternalController } from "./internal/internal.controller";
import { PubSubController } from "./pubsub/pubsub.controller";

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      expandVariables: true,
    }),
  ],
  controllers: [
    HealthController,
    GmailController,
    PubSubController,
    InternalController,
  ],
  providers: [RequestAuthService, GmailRepository, GmailStateService, GmailApiService, GmailIngestionService, GmailSyncService, GmailService],
})
export class AppModule {}
