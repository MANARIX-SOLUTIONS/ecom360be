package com.ecom360.integration.commerce.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CommerceConnectionCreateRequest(
    @NotNull UUID storeId,
    @NotBlank @Size(max = 64) String sourceType,
    @NotBlank @Size(max = 200) String label) {}
