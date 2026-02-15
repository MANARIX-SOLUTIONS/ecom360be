package com.ecom360.inventory.infrastructure.web;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.dto.*;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.UUID;

@RestController @RequestMapping(ApiConstants.API_BASE + "/stock")
@Tag(name = "Stock / Inventory") @SecurityRequirement(name = "bearerAuth")
public class StockController {
    private final StockService svc;
    public StockController(StockService svc) { this.svc = svc; }
    @PostMapping("/init") @Operation(summary = "Initialize stock") public ResponseEntity<StockLevelResponse> init(@Valid @RequestBody StockInitRequest r, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.status(201).body(svc.initializeStock(r, p)); }
    @PostMapping("/adjust") @Operation(summary = "Adjust stock") public ResponseEntity<StockMovementResponse> adjust(@Valid @RequestBody StockAdjustmentRequest r, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.status(201).body(svc.adjustStock(r, p)); }
    @GetMapping("/store/{storeId}") @Operation(summary = "Stock levels by store") public ResponseEntity<List<StockLevelResponse>> byStore(@PathVariable UUID storeId, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.ok(svc.getStockByStore(storeId, p)); }
    @GetMapping("/product/{productId}/store/{storeId}") @Operation(summary = "Stock level") public ResponseEntity<StockLevelResponse> level(@PathVariable UUID productId, @PathVariable UUID storeId, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.ok(svc.getStockLevel(productId, storeId, p)); }
    @GetMapping("/movements/product/{productId}/store/{storeId}") @Operation(summary = "Movements for product/store") public ResponseEntity<PageResponse<StockMovementResponse>> movements(@PathVariable UUID productId, @PathVariable UUID storeId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.ok(PageResponse.of(svc.getMovements(productId, storeId, p, PageRequest.of(page, Math.min(size, 100))))); }
    @GetMapping("/movements/store/{storeId}") @Operation(summary = "Movements by store") public ResponseEntity<PageResponse<StockMovementResponse>> movByStore(@PathVariable UUID storeId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal UserPrincipal p) { return ResponseEntity.ok(PageResponse.of(svc.getMovementsByStore(storeId, p, PageRequest.of(page, Math.min(size, 100))))); }
}
