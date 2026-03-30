package com.ecom360.integration.commerce.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CommerceConnectionCreateResponse(
    UUID id,
    UUID businessId,
    UUID storeId,
    String sourceType,
    String label,
    String incomingWebhookPath,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt,
    /** Secret HMAC ; afficher une seule fois au client (ne pas relire depuis l’API). */
    String hmacSecret) {}
