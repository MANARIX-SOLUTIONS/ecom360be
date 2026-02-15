package com.ecom360.inventory.application.dto;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
    UUID id,
    UUID productId,
    UUID storeId,
    UUID userId,
    String type,
    Integer quantity,
    Integer quantityBefore,
    Integer quantityAfter,
    String reference,
    String note,
    Instant createdAt) {}
