package com.ecom360.client.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record ClientPaymentRequest(
    @NotNull UUID storeId,
    @NotNull @Min(1) Integer amount,
    @NotBlank @Pattern(regexp = "cash|wave|orange_money") String paymentMethod,
    String note) {}
