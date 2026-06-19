import { Body, Controller, Get, Headers, Post, Query } from "@nestjs/common";
import { GmailService, StartConnectRequest, SyncRequestBody } from "./gmail.service";

@Controller()
export class GmailController {
  constructor(private readonly gmailService: GmailService) {}

  @Post("/v1/integrations/gmail/connect/start")
  startConnect(
    @Headers() headers: Record<string, string | undefined>,
    @Body() body: StartConnectRequest,
  ) {
    return this.gmailService.startConnect(headers, body);
  }

  @Get("/oauth/google/callback")
  callback(@Query() query: Record<string, string | undefined>) {
    return this.gmailService.handleCallback(query);
  }

  @Get("/v1/integrations/gmail/status")
  status(@Headers() headers: Record<string, string | undefined>) {
    return this.gmailService.getStatus(headers);
  }

  @Post("/v1/integrations/gmail/disconnect")
  disconnect(@Headers() headers: Record<string, string | undefined>) {
    return this.gmailService.disconnect(headers);
  }

  @Post("/v1/sync/request")
  requestSync(
    @Headers() headers: Record<string, string | undefined>,
    @Body() body: SyncRequestBody,
  ) {
    return this.gmailService.requestSync(headers, body);
  }
}
