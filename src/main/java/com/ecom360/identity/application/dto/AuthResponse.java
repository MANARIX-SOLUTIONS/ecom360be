package com.ecom360.identity.application.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UUID userId,
        String email,
        String fullName,
        UUID businessId,
        String role,
        String planSlug
) {}
