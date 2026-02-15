package com.ecom360.client.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
    UUID id,
    UUID businessId,
    String name,
    String phone,
    String email,
    String address,
    String notes,
    Integer creditBalance,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
