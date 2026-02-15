package com.ecom360.integration.application.dto;

import java.time.Instant;
import java.util.UUID;

public record WebhookResponse(
        UUID id,
        UUID businessId,
        String url,
        String events,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}
