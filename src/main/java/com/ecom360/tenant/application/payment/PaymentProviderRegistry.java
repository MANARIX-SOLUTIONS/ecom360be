package com.ecom360.tenant.application.payment;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {

  private final List<PaymentProvider> providers;

  public PaymentProviderRegistry(List<PaymentProvider> providers) {
    this.providers = providers;
  }

  public PaymentProvider get(String name) {
    return providers.stream()
        .filter(provider -> provider.getName().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported payment provider: " + name));
  }
}
