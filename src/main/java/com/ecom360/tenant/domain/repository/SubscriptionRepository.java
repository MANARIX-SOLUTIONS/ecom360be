package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  Optional<Subscription> findByBusinessIdAndStatus(UUID businessId, String status);

  Optional<Subscription> findFirstByBusinessIdOrderByCreatedAtDesc(UUID businessId);

  Optional<Subscription> findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
      UUID businessId, java.util.Collection<String> statuses);
}
