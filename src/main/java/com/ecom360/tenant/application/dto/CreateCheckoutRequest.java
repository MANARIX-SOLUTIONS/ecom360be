package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCheckoutRequest(
        @NotBlank String planSlug, @NotBlank String billingCycle, String paymentMethod) {
}
