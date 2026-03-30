package com.ecom360.integration.commerce.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CommerceConnectionResponse(
    UUID id,
    UUID businessId,
    UUID storeId,
    String sourceType,
    String label,
    /** Chemin relatif à concaténer à l’URL publique de l’API (ex. https://api.example.com). */
    String incomingWebhookPath,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt) {}
