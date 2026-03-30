package com.ecom360.integration.commerce.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CanonicalShippingPayload(
    @Min(0) Integer amountMinorUnits, @Size(max = 200) String method) {}
