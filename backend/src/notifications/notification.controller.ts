import { Body, Controller, Post } from "@nestjs/common";
import { NotificationService } from "./notification.service";

@Controller()
export class NotificationController {
  constructor(private readonly notificationService: NotificationService) {}

  @Post("/internal/notifications/carga-written")
  handleCargaWritten(@Body() body: unknown) {
    return this.notificationService.handleCargaWritten(body);
  }

  @Post("/internal/notifications/transaccion-created")
  handleTransaccionCreated(@Body() body: unknown) {
    return this.notificationService.handleTransaccionCreated(body);
  }
}
