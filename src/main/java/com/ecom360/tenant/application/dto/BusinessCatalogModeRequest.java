package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessCatalogModeRequest(
    @NotBlank @Size(max = 20) String catalogMode) {}
