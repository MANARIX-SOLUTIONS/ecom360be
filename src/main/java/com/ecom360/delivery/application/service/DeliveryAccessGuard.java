package com.ecom360.delivery.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.tenant.application.service.SubscriptionService;
import org.springframework.stereotype.Component;

@Component
class DeliveryAccessGuard {

  private final SubscriptionService subscriptionService;

  DeliveryAccessGuard(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  void requireBusiness(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }

  void requireDeliveryPlan(UserPrincipal p) {
    requireBusiness(p);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureDeliveryCouriers())) {
                throw new AccessDeniedException(
                    "Gestion des livreurs non incluse dans votre plan. Passez au plan Pro ou Business.");
              }
            });
  }
}
