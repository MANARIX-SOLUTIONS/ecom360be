package com.ecom360.tenant.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSubscriptionPaymentResponse(
        UUID intentId,
        UUID businessId,
        String businessName,
        UUID planId,
        String planSlug,
        String planName,
        String billingCycle,
        Integer amount,
        String currency,
        String provider,
        String channel,
        String status,
        String externalToken,
        String externalRef,
        String checkoutUrl,
        UUID subscriptionId,
        UUID invoiceId,
        String invoiceNumber,
        String failureReason,
        Instant paidAt,
        Instant createdAt,
        UUID createdByUserId) {
}
