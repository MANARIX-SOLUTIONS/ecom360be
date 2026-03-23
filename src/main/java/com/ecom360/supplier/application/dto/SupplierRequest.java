package com.ecom360.supplier.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 50) String phone,
    @Size(max = 255) String email,
    @Size(max = 100) String zone,
    String address,
    Boolean isActive) {
  public SupplierRequest {
    if (isActive == null) isActive = true;
  }
}
