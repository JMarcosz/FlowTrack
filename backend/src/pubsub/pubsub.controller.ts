import { Body, Controller, Post } from "@nestjs/common";
import { GmailService } from "../integrations/gmail/gmail.service";

@Controller()
export class PubSubController {
  constructor(private readonly gmailService: GmailService) {}

  @Post("/pubsub/gmail")
  handleGmail(@Body() body: unknown) {
    return this.gmailService.handlePubSubGmail(body);
  }

  @Post("/pubsub/email-ingestion")
  handleEmailIngestion(@Body() body: unknown) {
    return this.gmailService.handleEmailIngestion(body);
  }
}
