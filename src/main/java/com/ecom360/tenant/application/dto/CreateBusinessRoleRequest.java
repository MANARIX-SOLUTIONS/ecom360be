package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBusinessRoleRequest(@NotBlank @Size(max = 200) String name) {}
