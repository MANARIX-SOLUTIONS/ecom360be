package com.ecom360.supplier.application.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotEmpty; import jakarta.validation.constraints.NotNull;
import java.time.LocalDate; import java.util.List; import java.util.UUID;
public record PurchaseOrderRequest(@NotNull UUID supplierId, @NotNull UUID storeId, LocalDate expectedDate, String note, @NotEmpty @Valid List<PurchaseOrderLineRequest> lines) {}
