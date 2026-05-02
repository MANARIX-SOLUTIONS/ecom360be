package com.ecom360.tenant.application.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID id,
        UUID invoiceId,
        String invoiceNumber,
        String provider,
        String providerReference,
        String checkoutUrl,
        Integer amount,
        String currency,
        String status,
        String failureReason,
        Instant paidAt) {
}
