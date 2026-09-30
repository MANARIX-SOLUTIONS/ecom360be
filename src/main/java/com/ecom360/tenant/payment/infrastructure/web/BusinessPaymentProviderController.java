package com.ecom360.tenant.payment.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.payment.application.dto.BictorysConnectionTestResponse;
import com.ecom360.tenant.payment.application.dto.BictorysSettingsRequest;
import com.ecom360.tenant.payment.application.dto.BictorysSettingsResponse;
import com.ecom360.tenant.payment.application.service.BusinessPaymentProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/settings/payment-providers/bictorys")
@Tag(name = "Payment providers", description = "Business-owned PSP accounts (POS)")
@SecurityRequirement(name = "bearerAuth")
public class BusinessPaymentProviderController {

  private final BusinessPaymentProviderService service;

  public BusinessPaymentProviderController(BusinessPaymentProviderService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Get the business Bictorys account (keys masked)")
  public ResponseEntity<BictorysSettingsResponse> get(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(service.getBictorys(p));
  }

  @PutMapping
  @Operation(summary = "Create or update the business Bictorys account")
  public ResponseEntity<BictorysSettingsResponse> save(
      @Valid @RequestBody BictorysSettingsRequest req, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(service.saveBictorys(req, p));
  }

  @PostMapping("/test")
  @Operation(summary = "Check that Bictorys accepts the stored API key")
  public ResponseEntity<BictorysConnectionTestResponse> test(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(service.testBictorys(p));
  }
}
