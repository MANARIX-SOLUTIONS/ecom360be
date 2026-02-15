package com.ecom360.tenant.application.dto;

import java.util.UUID;

public record BusinessUserResponse(UUID id, UUID userId, String fullName, String email, String role, Boolean isActive) {}
