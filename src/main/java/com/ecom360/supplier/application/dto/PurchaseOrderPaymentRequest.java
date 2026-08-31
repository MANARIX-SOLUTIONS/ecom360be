package com.ecom360.supplier.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PurchaseOrderPaymentRequest(
    @NotNull @Min(1) Integer amount,
    @NotBlank @Pattern(regexp = "cash|wave|orange_money") String paymentMethod,
    String note) {}
