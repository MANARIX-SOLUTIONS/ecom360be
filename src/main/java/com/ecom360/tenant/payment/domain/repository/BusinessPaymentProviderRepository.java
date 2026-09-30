package com.ecom360.tenant.payment.domain.repository;

import com.ecom360.tenant.payment.domain.model.BusinessPaymentProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessPaymentProviderRepository
    extends JpaRepository<BusinessPaymentProvider, UUID> {

  Optional<BusinessPaymentProvider> findByBusinessIdAndProvider(UUID businessId, String provider);

  Optional<BusinessPaymentProvider> findByWebhookToken(String webhookToken);
}
