package com.ecom360.supplier.application.dto;

import java.time.Instant;
import java.util.UUID;

public record SupplierPaymentResponse(
    UUID id,
    UUID supplierId,
    UUID userId,
    Integer amount,
    String paymentMethod,
    String note,
    Instant createdAt) {}
