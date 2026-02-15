package com.ecom360.store.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreRequest(
        @NotBlank(message = "Store name is required") @Size(max = 255) String name,
        String address, @Size(max = 50) String phone, Boolean isActive
) { public StoreRequest { if (isActive == null) isActive = true; } }
