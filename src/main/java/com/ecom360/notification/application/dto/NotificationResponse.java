package com.ecom360.notification.application.dto;
import java.time.Instant; import java.util.UUID;
public record NotificationResponse(UUID id, UUID businessId, UUID userId, String type, String title, String body, String actionUrl, Boolean isRead, Instant readAt, Instant createdAt) {}
