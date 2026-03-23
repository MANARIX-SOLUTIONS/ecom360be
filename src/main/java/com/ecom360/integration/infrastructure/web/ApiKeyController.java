package com.ecom360.integration.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.application.dto.ApiKeyRequest;
import com.ecom360.integration.application.dto.ApiKeyResponse;
import com.ecom360.integration.application.service.ApiKeyService;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/api-keys")
@Tag(name = "API Keys", description = "Manage API keys for programmatic access")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyController {

  private final ApiKeyService apiKeyService;

  public ApiKeyController(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @PostMapping
  @Operation(summary = "Create API key (raw key returned only once)")
  public ResponseEntity<ApiKeyResponse> create(
      @Valid @RequestBody ApiKeyRequest request, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(apiKeyService.create(request, p));
  }

  @GetMapping
  @Operation(summary = "List API keys")
  public ResponseEntity<List<ApiKeyResponse>> list(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(apiKeyService.list(p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get API key")
  public ResponseEntity<ApiKeyResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(apiKeyService.getById(id, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Revoke API key")
  public ResponseEntity<Void> revoke(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    apiKeyService.revoke(id, p);
    return ResponseEntity.noContent().build();
  }
}
