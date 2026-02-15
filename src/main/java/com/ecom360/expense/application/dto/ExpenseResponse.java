package com.ecom360.expense.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
    UUID id,
    UUID businessId,
    UUID storeId,
    UUID userId,
    UUID categoryId,
    Integer amount,
    String description,
    LocalDate expenseDate,
    String receiptUrl,
    Instant createdAt,
    Instant updatedAt) {}
