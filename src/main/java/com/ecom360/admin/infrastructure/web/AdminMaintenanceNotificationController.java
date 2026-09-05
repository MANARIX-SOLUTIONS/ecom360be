package com.ecom360.admin.infrastructure.web;

import com.ecom360.admin.application.dto.AdminMaintenanceNotificationRequest;
import com.ecom360.admin.application.dto.AdminMaintenanceNotificationResult;
import com.ecom360.admin.application.service.AdminMaintenanceNotificationService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/notifications")
@Tag(name = "Admin Notifications", description = "Platform admin: bulk system notifications")
@SecurityRequirement(name = "bearerAuth")
public class AdminMaintenanceNotificationController {

  private final AdminMaintenanceNotificationService service;

  public AdminMaintenanceNotificationController(AdminMaintenanceNotificationService service) {
    this.service = service;
  }

  @PostMapping("/maintenance")
  @Operation(
      summary = "Notify businesses about a maintenance window",
      description =
          "Sends email to business contact and in-app notification to active users."
              + " If businessIds is empty, all businesses are targeted.")
  public ResponseEntity<AdminMaintenanceNotificationResult> notifyMaintenance(
      @Valid @RequestBody AdminMaintenanceNotificationRequest req) {
    return ResponseEntity.ok(service.notifyMaintenance(req));
  }
}
