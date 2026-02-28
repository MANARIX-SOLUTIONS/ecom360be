package com.ecom360.admin.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAssignPlanRequest(
    @NotBlank(message = "Plan slug is required") @Size(max = 50) String planSlug,
    @Size(max = 20) String billingCycle) {}
