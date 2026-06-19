import { Controller, Post } from "@nestjs/common";
import { MaintenanceService } from "./maintenance.service";

@Controller()
export class MaintenanceController {
  constructor(private readonly maintenanceService: MaintenanceService) {}

  @Post("/internal/jobs/gmail-maintenance")
  runMaintenance() {
    return this.maintenanceService.renewDueWatches();
  }
}
