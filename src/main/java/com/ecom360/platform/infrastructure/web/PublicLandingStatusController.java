package com.ecom360.platform.infrastructure.web;

import com.ecom360.platform.application.dto.LandingStatusResponse;
import com.ecom360.platform.application.service.LandingStatusService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/public/landing-status")
@Tag(name = "Public", description = "Statut public de la landing")
public class PublicLandingStatusController {

  private final LandingStatusService landingStatusService;

  public PublicLandingStatusController(LandingStatusService landingStatusService) {
    this.landingStatusService = landingStatusService;
  }

  @GetMapping
  @Operation(summary = "Indique si la landing est en écran de préparation du lancement")
  public ResponseEntity<LandingStatusResponse> getStatus() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(landingStatusService.getStatus());
  }
}
