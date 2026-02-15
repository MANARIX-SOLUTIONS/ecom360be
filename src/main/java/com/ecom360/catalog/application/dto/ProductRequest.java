package com.ecom360.catalog.application.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ProductRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 100) String sku,
    @Size(max = 100) String barcode,
    String description,
    @NotNull @Min(0) Integer costPrice,
    @NotNull @Min(0) Integer salePrice,
    @Size(max = 50) String unit,
    @Size(max = 500) String imageUrl,
    UUID categoryId,
    @NotNull Boolean isActive) {
  public ProductRequest {
    if (unit == null || unit.isBlank()) unit = "pièce";
    if (isActive == null) isActive = true;
  }
}
