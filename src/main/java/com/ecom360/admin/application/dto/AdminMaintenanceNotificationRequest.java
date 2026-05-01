package com.ecom360.admin.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminMaintenanceNotificationRequest(
    @NotBlank(message = "Title is required") @Size(max = 180) String title,
    @NotBlank(message = "Message is required") @Size(max = 4000) String message,
    @Size(max = 500) String statusPageUrl,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    List<UUID> businessIds) {}
