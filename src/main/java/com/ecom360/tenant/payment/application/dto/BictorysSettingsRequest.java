package com.ecom360.tenant.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Blank {@code apiKey} / {@code webhookSecret} keep the stored value (update without re-typing). */
public record BictorysSettingsRequest(
    @Size(max = 500) String apiKey,
    @Size(max = 500) String webhookSecret,
    @NotBlank @Pattern(regexp = "test|live", message = "environment must be test or live")
        String environment,
    @Pattern(regexp = "[A-Z]{2}", message = "country must be an ISO-2 code") String country,
    Boolean enabled) {}
