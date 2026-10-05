package com.ecom360.admin.infrastructure.web;

import com.ecom360.platform.application.dto.LandingStatusResponse;
import com.ecom360.platform.application.dto.LandingStatusUpdateRequest;
import com.ecom360.platform.application.service.LandingStatusService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/admin/platform")
@Tag(name = "Admin Platform", description = "Platform admin: landing launch gate")
@SecurityRequirement(name = "bearerAuth")
public class AdminLandingStatusController {

  private final LandingStatusService landingStatusService;

  public AdminLandingStatusController(LandingStatusService landingStatusService) {
    this.landingStatusService = landingStatusService;
  }

  @GetMapping("/landing-status")
  @Operation(summary = "Read whether the marketing landing is in pre-launch loading mode")
  public ResponseEntity<LandingStatusResponse> getStatus() {
    return ResponseEntity.ok(landingStatusService.getStatus());
  }

  @PutMapping("/landing-status")
  @Operation(summary = "Show or hide the marketing landing behind the pre-launch screen")
  public ResponseEntity<LandingStatusResponse> updateStatus(
      @Valid @RequestBody LandingStatusUpdateRequest request) {
    return ResponseEntity.ok(landingStatusService.update(request.loading()));
  }
}
