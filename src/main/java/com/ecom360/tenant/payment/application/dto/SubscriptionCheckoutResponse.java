package com.ecom360.tenant.payment.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code qrCode}, {@code paymentLink} and {@code ussdMessage} are only set
 * while the intent is pending.
 */
public record SubscriptionCheckoutResponse(
        UUID intentId,
        String status,
        String checkoutUrl,
        Integer amount,
        String currency,
        String planSlug,
        String billingCycle,
        String channel,
        String provider,
        UUID subscriptionId,
        UUID invoiceId,
        String failureReason,
        Instant paidAt,
        String qrCode,
        String paymentLink,
        String ussdMessage) {
}
