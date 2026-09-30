package com.ecom360.tenant.payment.application.dto;

import java.time.Instant;

/**
 * Keys are never returned in full. {@code webhookPath} is relative to the API
 * origin; the frontend prefixes it with its configured backend URL.
 */
public record BictorysSettingsResponse(
    boolean configured,
    boolean enabled,
    String environment,
    String country,
    String apiKeyMasked,
    String webhookSecretMasked,
    String webhookPath,
    Instant updatedAt) {}
