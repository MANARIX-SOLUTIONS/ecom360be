package com.ecom360.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(
        @NotBlank(message = "Full name is required") @Size(max = 255) String fullName,
        @NotBlank(message = "Email is required") @Email @Size(max = 255) String email,
        @Size(max = 50) String phone
) {}
