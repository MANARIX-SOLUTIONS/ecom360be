package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePlanRequest(
        @NotBlank(message = "Plan slug is required") @Size(max = 50) String planSlug,
        @Size(max = 20) String billingCycle
) {
    public ChangePlanRequest {
        if (billingCycle == null || billingCycle.isBlank()) {
            billingCycle = "monthly";
        }
    }
}
