package com.ecom360.client.infrastructure.web;

import com.ecom360.client.application.dto.*;
import com.ecom360.client.application.service.ClientService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/clients")
@Tag(name = "Clients")
@SecurityRequirement(name = "bearerAuth")
public class ClientController {

  private final ClientService svc;

  public ClientController(ClientService svc) {
    this.svc = svc;
  }

  @PostMapping
  @Operation(summary = "Create client")
  public ResponseEntity<ClientResponse> create(
      @Valid @RequestBody ClientRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.create(r, p));
  }

  @GetMapping
  @Operation(summary = "List clients")
  public ResponseEntity<PageResponse<ClientResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            svc.list(
                p, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()))));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get client")
  public ResponseEntity<ClientResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.getById(id, p));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update client")
  public ResponseEntity<ClientResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody ClientRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.update(id, r, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete client")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    svc.delete(id, p);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/payments")
  @Operation(summary = "Record client payment")
  public ResponseEntity<ClientPaymentResponse> pay(
      @PathVariable UUID id,
      @Valid @RequestBody ClientPaymentRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.recordPayment(id, r, p));
  }

  @GetMapping("/{id}/payments")
  @Operation(summary = "List client payments")
  public ResponseEntity<PageResponse<ClientPaymentResponse>> payments(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(svc.getPayments(id, p, PageRequest.of(page, Math.min(size, 100)))));
  }
}
