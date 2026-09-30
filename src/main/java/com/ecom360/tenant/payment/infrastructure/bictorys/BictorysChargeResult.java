package com.ecom360.tenant.payment.infrastructure.bictorys;

/**
 * Response of {@code POST /pay/v1/charges}. {@code qrCode} is a base64 PNG
 * (Wave), {@code link} a deep link, {@code message} a USSD instruction.
 */
public record BictorysChargeResult(
    String transactionId, String redirectUrl, String link, String qrCode, String message) {}
