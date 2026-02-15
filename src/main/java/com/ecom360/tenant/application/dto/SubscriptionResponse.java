package com.ecom360.tenant.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionResponse(
    UUID id,
    UUID planId,
    String planSlug,
    String planName,
    String billingCycle,
    String status,
    LocalDate currentPeriodStart,
    LocalDate currentPeriodEnd) {}
