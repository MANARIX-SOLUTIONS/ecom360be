package com.ecom360.admin.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminBusinessResponse(
    UUID id,
    String name,
    String owner,
    String email,
    String phone,
    String address,
    String plan,
    String status,
    int storesCount,
    String revenue,
    Instant createdAt,
    LocalDate trialEndsAt,
    AdminBusinessSubscriptionInfo subscription) {}
