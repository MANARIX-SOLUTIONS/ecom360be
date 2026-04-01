package com.ecom360.identity.application.dto;

import java.time.Instant;
import java.util.UUID;

public record DemoRequestResponse(
    UUID id,
    String fullName,
    String email,
    String phone,
    String businessName,
    String message,
    String jobTitle,
    String city,
    String sector,
    String status,
    Instant createdAt,
    Instant reviewedAt,
    UUID reviewedByUserId,
    String rejectionReason) {}
