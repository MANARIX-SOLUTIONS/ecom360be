package com.ecom360.sales.application.dto;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record SaleResponse(UUID id, UUID businessId, UUID storeId, String storeName, String storeAddress, UUID userId, UUID clientId, String receiptNumber, String paymentMethod, Integer subtotal, Integer discountAmount, Integer total, Integer amountReceived, Integer changeGiven, String status, String note, List<SaleLineResponse> lines, Instant createdAt) {}
