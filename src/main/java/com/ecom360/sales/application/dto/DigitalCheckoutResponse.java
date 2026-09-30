package com.ecom360.sales.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code qrCode}, {@code paymentLink} and {@code ussdMessage} are only set while
 * pending; {@code sale} is set once the payment is settled (receipt).
 */
public record DigitalCheckoutResponse(
    UUID intentId,
    String status,
    String channel,
    int amount,
    String currency,
    UUID saleId,
    String receiptNumber,
    String checkoutUrl,
    String qrCode,
    String paymentLink,
    String ussdMessage,
    Instant expiresAt,
    String failureReason,
    SaleResponse sale) {}
