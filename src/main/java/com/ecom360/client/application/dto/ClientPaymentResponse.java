package com.ecom360.client.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientPaymentResponse(
    UUID id,
    UUID clientId,
    UUID storeId,
    UUID userId,
    Integer amount,
    String paymentMethod,
    String note,
    Instant createdAt
) {}
