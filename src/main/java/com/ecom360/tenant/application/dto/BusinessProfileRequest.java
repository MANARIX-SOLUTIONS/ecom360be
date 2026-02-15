package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessProfileRequest(
    @NotBlank(message = "Name is required") @Size(max = 255) String name,
    @NotBlank(message = "Email is required") @Email @Size(max = 255) String email,
    @Size(max = 50) String phone,
    @Size(max = 1000) String address) {}
