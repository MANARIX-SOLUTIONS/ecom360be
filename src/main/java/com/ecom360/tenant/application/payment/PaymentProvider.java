package com.ecom360.tenant.application.payment;

public interface PaymentProvider {

  String getName();

  CheckoutCreateResponse createCheckout(CheckoutCreateRequest request);

  PaymentCallbackResult parseCallback(String rawPayload);
}
