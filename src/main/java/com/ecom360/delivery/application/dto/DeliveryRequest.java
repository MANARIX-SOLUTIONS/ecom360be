package com.ecom360.delivery.application.dto;

import com.ecom360.delivery.domain.model.DeliveryStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DeliveryRequest(
    @NotNull UUID courierId,
    UUID saleId,
    @NotBlank String status,
    @Min(1) int parcelsCount,
    @Size(max = 2000) String notes) {
  public DeliveryRequest {
    if (parcelsCount < 1)
      parcelsCount = 1;
    if (notes != null && notes.isBlank())
      notes = null;
  }

  public DeliveryStatus parsedStatus() {
    return DeliveryStatus.from(status);
  }
}
