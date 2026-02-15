package com.ecom360.expense.infrastructure.web;

import com.ecom360.expense.application.dto.*;
import com.ecom360.expense.application.service.ExpenseService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/expenses")
@Tag(name = "Expenses")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {
  private final ExpenseService svc;

  public ExpenseController(ExpenseService svc) {
    this.svc = svc;
  }

  @PostMapping("/categories")
  @Operation(summary = "Create expense category")
  public ResponseEntity<ExpenseCategoryResponse> createCat(
      @Valid @RequestBody ExpenseCategoryRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.createCategory(r, p));
  }

  @GetMapping("/categories")
  @Operation(summary = "List expense categories")
  public ResponseEntity<List<ExpenseCategoryResponse>> listCats(
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.listCategories(p));
  }

  @PutMapping("/categories/{id}")
  @Operation(summary = "Update expense category")
  public ResponseEntity<ExpenseCategoryResponse> updateCat(
      @PathVariable UUID id,
      @Valid @RequestBody ExpenseCategoryRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.updateCategory(id, r, p));
  }

  @DeleteMapping("/categories/{id}")
  @Operation(summary = "Delete expense category")
  public ResponseEntity<Void> deleteCat(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    svc.deleteCategory(id, p);
    return ResponseEntity.noContent().build();
  }

  @PostMapping
  @Operation(summary = "Create expense")
  public ResponseEntity<ExpenseResponse> create(
      @Valid @RequestBody ExpenseRequest r, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(201).body(svc.create(r, p));
  }

  @GetMapping
  @Operation(summary = "List expenses")
  public ResponseEntity<PageResponse<ExpenseResponse>> list(
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID storeId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(
        PageResponse.of(
            svc.list(p, categoryId, storeId, PageRequest.of(page, Math.min(size, 100)))));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get expense")
  public ResponseEntity<ExpenseResponse> get(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.getById(id, p));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update expense")
  public ResponseEntity<ExpenseResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody ExpenseRequest r,
      @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.ok(svc.update(id, r, p));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete expense")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
    svc.delete(id, p);
    return ResponseEntity.noContent().build();
  }
}
