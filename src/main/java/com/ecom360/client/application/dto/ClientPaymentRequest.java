package com.ecom360.client.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClientPaymentRequest(
    @NotNull UUID storeId,
    @NotNull @Min(1) Integer amount,
    @NotBlank String paymentMethod,
    String note
) {}
