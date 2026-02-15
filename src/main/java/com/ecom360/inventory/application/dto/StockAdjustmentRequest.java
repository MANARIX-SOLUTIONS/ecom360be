package com.ecom360.inventory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StockAdjustmentRequest(
    @NotNull UUID productId,
    @NotNull UUID storeId,
    @NotNull Integer quantity,
    @NotBlank String type,
    String reference,
    String note) {}
