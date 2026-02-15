package com.ecom360.inventory.application.dto;
import jakarta.validation.constraints.Min; import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record StockInitRequest(@NotNull UUID productId, @NotNull UUID storeId, @NotNull @Min(0) Integer quantity, @Min(0) Integer minStock) {
    public StockInitRequest { if (minStock == null) minStock = 0; }
}
