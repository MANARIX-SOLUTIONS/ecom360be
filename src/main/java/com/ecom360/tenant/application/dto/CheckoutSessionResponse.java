package com.ecom360.tenant.application.dto;

import java.util.UUID;

public record CheckoutSessionResponse(
        UUID transactionId,
        UUID invoiceId,
        String invoiceNumber,
        String provider,
        String providerReference,
        String checkoutUrl,
        Integer amount,
        String currency,
        String status) {
}
