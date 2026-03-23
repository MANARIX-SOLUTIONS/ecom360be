package com.ecom360.store.application.dto;

import java.time.Instant;
import java.util.UUID;

public record StoreResponse(
    UUID id,
    UUID businessId,
    String name,
    String address,
    String phone,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt) {}
