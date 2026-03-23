package com.ecom360.integration.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.application.dto.WebhookCreateResponse;
import com.ecom360.integration.application.dto.WebhookRequest;
import com.ecom360.integration.application.dto.WebhookResponse;
import com.ecom360.integration.application.service.WebhookService;
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
@RequestMapping(ApiConstants.API_BASE + "/webhooks")
@Tag(name = "Webhooks", description = "Manage webhooks for event notifications")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {

  private final WebhookService webhookService;

  public WebhookController(WebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @PostMapping
  @Operation(summary = "Create webhook (secret returned only once)")
  public ResponseEntity<WebhookCreateResponse> create(
      @Valid @RequestBody WebhookRequest request, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(webhookService.create(request, p));
  }

  @GetMapping
  @Operation(summary = "List webhooks")
  public ResponseEntity<List<WebhookResponse>> list(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(webhookService.list(p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get webhook")
  public ResponseEntity<WebhookResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(webhookService.getById(id, p));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update webhook")
  public ResponseEntity<WebhookResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody WebhookRequest request,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(webhookService.update(id, request, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete webhook")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    webhookService.delete(id, p);
    return ResponseEntity.noContent().build();
  }
}
