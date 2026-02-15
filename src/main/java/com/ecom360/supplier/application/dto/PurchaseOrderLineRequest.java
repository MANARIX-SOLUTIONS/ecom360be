package com.ecom360.supplier.application.dto;
import jakarta.validation.constraints.Min; import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record PurchaseOrderLineRequest(@NotNull UUID productId, @NotNull @Min(1) Integer quantity, @NotNull @Min(0) Integer unitCost) {}
