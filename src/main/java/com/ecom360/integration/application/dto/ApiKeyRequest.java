package com.ecom360.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ApiKeyRequest(
        @NotBlank @Size(max = 255) String label,
        @NotBlank @Size(max = 100) String permissions,
        LocalDate expiresAt
) {}
