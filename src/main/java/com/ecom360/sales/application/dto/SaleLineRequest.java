package com.ecom360.sales.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SaleLineRequest(@NotNull UUID productId, @NotNull @Min(1) Integer quantity) {}
