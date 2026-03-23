package com.ecom360.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebhookRequest(
    @NotBlank @Size(max = 500) String url,
    @NotBlank @Size(max = 500) String events,
    Boolean isActive) {
  public WebhookRequest {
    if (isActive == null) isActive = true;
  }
}
