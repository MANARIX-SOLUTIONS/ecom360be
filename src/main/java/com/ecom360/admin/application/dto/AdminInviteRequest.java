package com.ecom360.admin.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AdminInviteRequest(
    @NotBlank(message = "Email is required") @Email @Size(max = 255) String email,
    @NotBlank(message = "Full name is required") @Size(max = 255) String fullName,
    @NotBlank(message = "Role is required") @Size(max = 50) String role,
    @NotNull(message = "Business ID is required") UUID businessId) {}
