package com.ecom360.integration.commerce.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Corps JSON attendu pour l’ingestion « générique » (webhook entrant). Les connecteurs dédiés
 * (WooCommerce, etc.) pourront transformer leur payload vers ce modèle.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalOrderPayload(
    @NotBlank @Size(max = 64) String sourceType,
    @NotBlank @Size(max = 512) String externalOrderId,
    Instant externalUpdatedAt,
    @NotBlank @Size(max = 16) String currency,
    @NotEmpty @Valid List<CanonicalOrderLinePayload> lines,
    @Valid CanonicalCustomerPayload customer,
    @NotBlank @Size(max = 32) String paymentStatus,
    @Valid CanonicalShippingPayload shipping,
    Map<String, Object> metadata) {}
