package com.ecom360.catalog.application.dto;
import java.time.Instant; import java.util.UUID;
public record ProductResponse(UUID id, UUID businessId, UUID categoryId, String name, String sku, String barcode, String description, Integer costPrice, Integer salePrice, String unit, String imageUrl, Boolean isActive, Instant createdAt, Instant updatedAt) {}
