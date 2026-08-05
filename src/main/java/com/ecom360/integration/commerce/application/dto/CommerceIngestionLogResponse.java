package com.ecom360.integration.commerce.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CommerceIngestionLogResponse(
        UUID id,
        UUID connectionId,
        UUID businessId,
        String sourceType,
        String externalOrderId,
        String status,
        String errorMessage,
        UUID saleId,
        Instant createdAt) {
}
