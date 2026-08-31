package com.ecom360.sales.application.dto;

import java.time.Instant;
import java.util.UUID;

public record SalePaymentResponse(
    UUID id,
    UUID saleId,
    UUID storeId,
    UUID userId,
    Integer amount,
    String paymentMethod,
    String kind,
    String note,
    Instant createdAt) {}
