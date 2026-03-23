package com.ecom360.tenant.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.application.dto.BusinessLogoRequest;
import com.ecom360.tenant.application.dto.BusinessProfileRequest;
import com.ecom360.tenant.application.dto.BusinessProfileResponse;
import com.ecom360.tenant.application.service.BusinessProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/business")
@Tag(name = "Business", description = "Business profile management")
@SecurityRequirement(name = "bearerAuth")
public class BusinessController {

  private final BusinessProfileService businessProfileService;

  public BusinessController(BusinessProfileService businessProfileService) {
    this.businessProfileService = businessProfileService;
  }

  @GetMapping("/me")
  @Operation(summary = "Get current business profile")
  public ResponseEntity<BusinessProfileResponse> getMe(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(businessProfileService.get(p));
  }

  @PutMapping("/me")
  @Operation(summary = "Update current business profile")
  public ResponseEntity<BusinessProfileResponse> updateMe(
      @Valid @RequestBody BusinessProfileRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(businessProfileService.update(req, p));
  }

  @PatchMapping("/me/logo")
  @Operation(summary = "Mettre à jour le logo (plan Business si URL non vide)")
  public ResponseEntity<BusinessProfileResponse> updateLogo(
      @Valid @RequestBody BusinessLogoRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(businessProfileService.updateLogo(req, p));
  }
}
