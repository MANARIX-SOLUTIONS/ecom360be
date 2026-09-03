package com.ecom360.catalog.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 20) String color,
    Integer sortOrder,
    UUID parentId) {
  public CategoryRequest {
    if (sortOrder == null) sortOrder = 0;
  }
}
