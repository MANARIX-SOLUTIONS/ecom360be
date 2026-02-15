package com.ecom360.catalog.application.dto;
import java.time.Instant; import java.util.UUID;
public record CategoryResponse(UUID id, UUID businessId, String name, String color, Integer sortOrder, Instant createdAt) {}
