package com.ecom360.sales.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record SaleRequest(
    @NotNull UUID storeId,
    @NotNull UUID clientId,
    @NotBlank @Pattern(regexp = "cash|wave|orange_money|credit") String paymentMethod,
    @Min(0) Integer discountAmount,
    @Min(0) Integer amountReceived,
    String note,
    @NotEmpty @Valid List<SaleLineRequest> lines,
    @Schema(description = "Idempotence POS / outbox offline — unique par business")
        UUID clientSaleId) {
  public SaleRequest {
    if (discountAmount == null)
      discountAmount = 0;
  }
}
