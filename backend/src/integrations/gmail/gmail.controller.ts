import { Body, Controller, Get, Headers, Post, Query } from "@nestjs/common";
import { GmailService } from "./gmail.service";

@Controller()
export class GmailController {
  constructor(private readonly gmailService: GmailService) {}

  @Post("/v1/integrations/gmail/connect/start")
  startConnect(@Headers("x-firebase-auth-uid") uid = "unknown") {
    return this.gmailService.startConnect(uid);
  }

  @Get("/oauth/google/callback")
  callback(@Query() query: Record<string, string | undefined>) {
    return this.gmailService.handleCallback(query);
  }

  @Get("/v1/integrations/gmail/status")
  status(@Headers("x-firebase-auth-uid") uid = "unknown") {
    return this.gmailService.getStatus(uid);
  }

  @Post("/v1/integrations/gmail/disconnect")
  disconnect(@Headers("x-firebase-auth-uid") uid = "unknown") {
    return this.gmailService.disconnect(uid);
  }

  @Post("/v1/sync/request")
  requestSync(@Headers("x-firebase-auth-uid") uid = "unknown") {
    return this.gmailService.requestSync(uid);
  }

  @Post("/v1/integrations/gmail/connect/state")
  setState(@Body() body: Record<string, unknown>) {
    return { accepted: true, body };
  }
}
