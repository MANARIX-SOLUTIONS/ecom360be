package com.ecom360.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record SaleRequest(
    @NotNull UUID storeId,
    @NotNull UUID clientId,
    @NotBlank String paymentMethod,
    @Min(0) Integer discountAmount,
    @Min(0) Integer amountReceived,
    String note,
    @NotEmpty @Valid List<SaleLineRequest> lines) {
  public SaleRequest {
    if (discountAmount == null)
      discountAmount = 0;
  }
}
