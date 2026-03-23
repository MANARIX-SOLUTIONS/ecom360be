package com.ecom360.store.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.store.application.dto.*;
import com.ecom360.store.application.service.StoreService;
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
@RequestMapping(ApiConstants.API_BASE + "/stores")
@Tag(name = "Stores")
@SecurityRequirement(name = "bearerAuth")
public class StoreController {
  private final StoreService svc;

  public StoreController(StoreService svc) {
    this.svc = svc;
  }

  @PostMapping
  @Operation(summary = "Create store")
  public ResponseEntity<StoreResponse> create(
      @Valid @RequestBody StoreRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.create(r, p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get store")
  public ResponseEntity<StoreResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.getById(id, p));
  }

  @GetMapping
  @Operation(summary = "List stores")
  public ResponseEntity<List<StoreResponse>> list(@AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.list(p));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update store")
  public ResponseEntity<StoreResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody StoreRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.update(id, r, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete store")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    svc.delete(id, p);
    return ResponseEntity.noContent().build();
  }
}
