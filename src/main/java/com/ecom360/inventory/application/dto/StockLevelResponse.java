package com.ecom360.inventory.application.dto;

import java.time.Instant;
import java.util.UUID;

public record StockLevelResponse(
    UUID id,
    UUID productId,
    String productName,
    UUID storeId,
    String storeName,
    Integer quantity,
    Integer minStock,
    Boolean lowStock,
    Instant updatedAt,
    Integer salePrice,
    UUID categoryId) {}
