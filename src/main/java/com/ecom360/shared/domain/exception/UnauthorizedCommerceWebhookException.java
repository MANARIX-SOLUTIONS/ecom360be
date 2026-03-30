package com.ecom360.shared.domain.exception;

/** Webhook commerce entrant : signature HMAC absente ou invalide (réponse HTTP 401). */
public class UnauthorizedCommerceWebhookException extends RuntimeException {

  public UnauthorizedCommerceWebhookException(String message) {
    super(message);
  }
}
