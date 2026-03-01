package com.ecom360.delivery.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourierRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 50) String phone,
    @Size(max = 255) String email,
    Boolean isActive) {
  public CourierRequest {
    if (isActive == null) isActive = true;
  }
}
