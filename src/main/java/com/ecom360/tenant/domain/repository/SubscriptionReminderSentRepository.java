package com.ecom360.tenant.domain.repository;

import com.ecom360.tenant.domain.model.SubscriptionReminderSent;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionReminderSentRepository
    extends JpaRepository<SubscriptionReminderSent, UUID> {

  boolean existsBySubscriptionIdAndReminderKindAndPeriodEnd(
      UUID subscriptionId, String reminderKind, LocalDate periodEnd);
}
