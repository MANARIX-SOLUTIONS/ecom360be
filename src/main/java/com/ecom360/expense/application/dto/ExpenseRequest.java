package com.ecom360.expense.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseRequest(
    UUID storeId,
    @NotNull UUID categoryId,
    @NotNull @Min(1) Integer amount,
    String description,
    @NotNull LocalDate expenseDate,
    String receiptUrl) {}
