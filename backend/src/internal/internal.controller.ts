import { Body, Controller, Post } from "@nestjs/common";
import { GmailService } from "../integrations/gmail/gmail.service";

@Controller()
export class InternalController {
  constructor(private readonly gmailService: GmailService) {}

  @Post("/internal/watch/renew")
  renewWatch(@Body() body: unknown) {
    return this.gmailService.renewWatch(body);
  }
}
