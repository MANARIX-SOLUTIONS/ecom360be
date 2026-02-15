package com.ecom360.admin.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String name,
        String email,
        String role,
        String business,
        String status,
        Instant lastLoginAt,
        Instant createdAt
) {}
