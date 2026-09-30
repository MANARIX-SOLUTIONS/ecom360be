package com.ecom360.sales.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

/** POS sale paid by Wave / Orange Money through the business's Bictorys account. */
public record DigitalCheckoutRequest(
    @NotNull UUID storeId,
    @NotNull UUID clientId,
    @NotBlank @Pattern(regexp = "wave|orange_money") String channel,
    @Min(0) Integer discountAmount,
    String note,
    @NotEmpty @Valid List<SaleLineRequest> lines) {
  public DigitalCheckoutRequest {
    if (discountAmount == null) discountAmount = 0;
  }
}
