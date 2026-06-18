import { Controller, Get } from "@nestjs/common";

@Controller()
export class HealthController {
  @Get("/health")
  health() {
    return {
      ok: true,
      service: "flowtrack-backend",
      mode: process.env.SERVICE_MODE ?? "web",
      timestamp: new Date().toISOString(),
    };
  }
}
