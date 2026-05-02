package com.ecom360.tenant.application.payment;

import java.util.Map;
import java.util.UUID;

public record CheckoutCreateRequest(
        UUID transactionId,
        UUID businessId,
        UUID subscriptionId,
        String invoiceNumber,
        String planName,
        String planSlug,
        String billingCycle,
        Integer amount,
        String currency,
        String customerName,
        String customerEmail,
        String paymentMethod,
        Map<String, String> metadata) {
}
