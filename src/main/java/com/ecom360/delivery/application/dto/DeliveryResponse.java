package com.ecom360.delivery.application.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
    UUID id,
    UUID businessId,
    UUID courierId,
    UUID saleId,
    String status,
    int parcelsCount,
    Instant deliveredAt,
    String notes,
    Instant createdAt,
    Instant updatedAt) {}
