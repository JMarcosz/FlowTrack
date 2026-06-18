import { Module } from "@nestjs/common";
import { ConfigModule } from "@nestjs/config";
import { HealthController } from "./health/health.controller";
import { GmailController } from "./integrations/gmail/gmail.controller";
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
  providers: [GmailService],
})
export class AppModule {}
