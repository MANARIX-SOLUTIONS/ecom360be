package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @NotBlank(message = "Email is required") @Email @Size(max = 255) String email,
        @NotBlank(message = "Role is required") @Size(max = 50) String role
) {}
