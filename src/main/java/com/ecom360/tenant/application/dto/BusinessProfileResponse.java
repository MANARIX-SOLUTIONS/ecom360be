package com.ecom360.tenant.application.dto;

import java.time.Instant;
import java.util.UUID;

public record BusinessProfileResponse(
    UUID id,
    String name,
    String email,
    String phone,
    String address,
    String logoUrl,
    Instant createdAt) {}
