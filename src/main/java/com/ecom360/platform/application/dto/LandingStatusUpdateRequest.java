package com.ecom360.platform.application.dto;

import jakarta.validation.constraints.NotNull;

public record LandingStatusUpdateRequest(@NotNull Boolean loading) {}
