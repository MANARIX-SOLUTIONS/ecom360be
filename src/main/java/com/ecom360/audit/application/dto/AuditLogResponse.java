package com.ecom360.audit.application.dto;
import java.time.Instant; import java.util.Map; import java.util.UUID;
public record AuditLogResponse(UUID id, UUID businessId, UUID userId, String action, String entityType, UUID entityId, Map<String, Object> changes, String ipAddress, Instant createdAt) {}
