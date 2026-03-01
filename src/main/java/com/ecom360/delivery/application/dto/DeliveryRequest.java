package com.ecom360.delivery.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeliveryRequest(
    @NotNull UUID courierId,
    UUID saleId,
    @NotNull String status, // delivered, failed, cancelled
    @Min(1) int parcelsCount,
    String notes) {
  public DeliveryRequest {
    if (parcelsCount < 1) parcelsCount = 1;
  }
}
