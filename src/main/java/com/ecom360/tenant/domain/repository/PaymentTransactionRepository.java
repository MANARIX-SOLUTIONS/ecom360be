package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.PaymentTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

  Optional<PaymentTransaction> findByProviderAndProviderReference(
      String provider, String providerReference);
}
