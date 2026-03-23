package com.ecom360.delivery.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CourierResponse(
    UUID id,
    UUID businessId,
    String name,
    String phone,
    String email,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt) {}
