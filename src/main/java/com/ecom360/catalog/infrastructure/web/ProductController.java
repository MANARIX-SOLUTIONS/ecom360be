package com.ecom360.catalog.infrastructure.web;

import com.ecom360.catalog.application.dto.*;
import com.ecom360.catalog.application.service.ProductService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/products")
@Tag(name = "Products")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {
  private final ProductService svc;

  public ProductController(ProductService svc) {
    this.svc = svc;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PRODUCTS_CREATE') or hasRole('PLATFORM_ADMIN')")
  @Operation(summary = "Create product")
  public ResponseEntity<ProductResponse> create(
      @Valid @RequestBody ProductRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.create(r, p));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get product")
  public ResponseEntity<ProductResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.getById(id, p));
  }

  @GetMapping
  @Operation(summary = "List products")
  public ResponseEntity<PageResponse<ProductResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID storeId,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            svc.list(
                p,
                PageRequest.of(
                    page,
                    Math.min(size, ApiConstants.MAX_PAGE_SIZE),
                    Sort.by("createdAt").descending()),
                search,
                storeId)));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update product")
  public ResponseEntity<ProductResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody ProductRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.update(id, r, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete product")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    svc.delete(id, p);
    return ResponseEntity.noContent().build();
  }
}
