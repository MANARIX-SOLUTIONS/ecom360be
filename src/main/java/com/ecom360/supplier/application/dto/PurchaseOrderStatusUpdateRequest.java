package com.ecom360.supplier.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record PurchaseOrderStatusUpdateRequest(
    @NotBlank String status,
    @Min(0) Integer amountPaid,
    @Pattern(regexp = "cash|wave|orange_money") String paymentMethod,
    LocalDate dueDate) {}
