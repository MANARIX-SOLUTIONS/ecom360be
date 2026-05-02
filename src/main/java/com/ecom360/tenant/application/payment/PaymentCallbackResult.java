package com.ecom360.tenant.application.payment;

public record PaymentCallbackResult(
        String providerReference, String status, String failureReason, String rawPayload) {
}
