package com.ecom360.supplier.application.dto;
import jakarta.validation.constraints.Min; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
public record SupplierPaymentRequest(@NotNull @Min(1) Integer amount, @NotBlank String paymentMethod, String note) {}
