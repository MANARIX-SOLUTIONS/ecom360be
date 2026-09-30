package com.ecom360.sales.application.dto;

/** {@code available} requires both the Business plan and a configured Bictorys account. */
public record DigitalCheckoutAvailabilityResponse(
    boolean planAllowed, boolean configured, boolean available) {}
