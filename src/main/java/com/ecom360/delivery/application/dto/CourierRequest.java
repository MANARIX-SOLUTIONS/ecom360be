package com.ecom360.delivery.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourierRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 50) String phone,
    @Email @Size(max = 255) String email,
    Boolean isActive) {
  public CourierRequest {
    if (isActive == null) isActive = true;
    if (name != null) name = name.trim();
    if (phone != null && phone.isBlank()) phone = null;
    else if (phone != null) phone = phone.trim();
    if (email != null && email.isBlank()) email = null;
    else if (email != null) email = email.trim();
  }
}
