package com.ecom360.supplier.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
    UUID id,
    UUID businessId,
    UUID supplierId,
    UUID storeId,
    UUID userId,
    String reference,
    String status,
    Integer totalAmount,
    LocalDate expectedDate,
    LocalDate receivedDate,
    String note,
    List<PurchaseOrderLineResponse> lines,
    Instant createdAt,
    Instant updatedAt) {}
