package com.ecom360.supplier.application.dto;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderPaymentResponse(
    UUID id,
    UUID purchaseOrderId,
    UUID storeId,
    UUID userId,
    Integer amount,
    String paymentMethod,
    String kind,
    String note,
    Instant createdAt) {}
