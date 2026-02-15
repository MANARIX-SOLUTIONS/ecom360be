package com.ecom360.expense.application.dto;
import java.time.Instant; import java.util.UUID;
public record ExpenseCategoryResponse(UUID id, UUID businessId, String name, String color, Integer sortOrder, Instant createdAt) {}
