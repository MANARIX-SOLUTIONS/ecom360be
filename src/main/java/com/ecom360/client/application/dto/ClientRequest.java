package com.ecom360.client.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 50) String phone,
    @Size(max = 255) String email,
    @Size(max = 500) String address,
    @Size(max = 1000) String notes,
    Boolean isActive
) {
    public ClientRequest {
        if (isActive == null) isActive = true;
    }
}
