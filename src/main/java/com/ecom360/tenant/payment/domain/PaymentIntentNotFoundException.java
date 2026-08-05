package com.ecom360.tenant.payment.domain;

/**
 * Raised when an IPN refers to an unknown intent — callers should return 5xx
 * for PSP retry.
 */
public class PaymentIntentNotFoundException extends RuntimeException {

  public PaymentIntentNotFoundException(String message) {
    super(message);
  }
}
