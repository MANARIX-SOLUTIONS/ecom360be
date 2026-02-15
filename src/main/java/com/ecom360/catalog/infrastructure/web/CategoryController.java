package com.ecom360.catalog.infrastructure.web;

import com.ecom360.catalog.application.dto.*;
import com.ecom360.catalog.application.service.CategoryService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.UUID;

@RestController @RequestMapping(ApiConstants.API_BASE + "/categories")
@Tag(name = "Categories") @SecurityRequirement(name = "bearerAuth")
public class CategoryController {
    private final CategoryService svc;
    public CategoryController(CategoryService svc) { this.svc = svc; }
    @PostMapping @Operation(summary = "Create category") public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest r, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.status(201).body(svc.create(r, p)); }
    @GetMapping @Operation(summary = "List categories") public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.ok(svc.list(p)); }
    @GetMapping("/{id}") @Operation(summary = "Get category") public ResponseEntity<CategoryResponse> get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.ok(svc.getById(id, p)); }
    @PutMapping("/{id}") @Operation(summary = "Update category") public ResponseEntity<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest r, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.ok(svc.update(id, r, p)); }
    @DeleteMapping("/{id}") @Operation(summary = "Delete category") public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) { svc.delete(id, p); return ResponseEntity.noContent().build(); }
}
