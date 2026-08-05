package com.ecom360.integration.commerce.application.dto;

import jakarta.validation.constraints.NotNull;

public record CommerceConnectionUpdateRequest(@NotNull Boolean isActive) {
}
