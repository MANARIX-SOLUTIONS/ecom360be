package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.Subscription;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  Optional<Subscription> findByBusinessIdAndStatus(UUID businessId, String status);

  Optional<Subscription> findFirstByBusinessIdOrderByCreatedAtDesc(UUID businessId);

  Optional<Subscription> findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
      UUID businessId, java.util.Collection<String> statuses);

  /** Trialing or active subscriptions past due for expiration. */
  @Query(
      """
    SELECT s FROM Subscription s
    WHERE s.status IN ('trialing', 'active')
    AND s.currentPeriodEnd < :before
    AND (s.cancelAtPeriodEnd = false OR s.cancelAtPeriodEnd IS NULL)
    """)
  List<Subscription> findExpiredTrialsAndSubscriptions(@Param("before") LocalDate before);

  /** Active subscriptions with cancel_at_period_end that reached period end. */
  @Query(
      """
    SELECT s FROM Subscription s
    WHERE s.status IN ('trialing', 'active')
    AND s.currentPeriodEnd < :before
    AND s.cancelAtPeriodEnd = true
    """)
  List<Subscription> findCancelledAtPeriodEnd(@Param("before") LocalDate before);
}
