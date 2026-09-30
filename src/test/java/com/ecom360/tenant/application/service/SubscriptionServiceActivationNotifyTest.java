package com.ecom360.tenant.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.shared.infrastructure.cache.CachedLookups;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceActivationNotifyTest {

  @Mock
  SubscriptionRepository subscriptionRepository;
  @Mock
  PlanRepository planRepository;
  @Mock
  BusinessRepository businessRepository;
  @Mock
  RolePermissionService permissionService;
  @Mock
  SubscriptionCheckoutNotificationService checkoutNotificationService;
  @Mock
  CachedLookups cachedLookups;
  @Mock
  PlatformTransactionManager transactionManager;

  SubscriptionService service;
  UUID businessId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new SubscriptionService(
        subscriptionRepository,
        planRepository,
        businessRepository,
        permissionService,
        checkoutNotificationService,
        cachedLookups,
        transactionManager);
    Plan plan = new Plan();
    plan.setId(UUID.randomUUID());
    plan.setSlug("pro");
    plan.setName("Pro");
    plan.setIsActive(true);
    when(planRepository.findBySlug("pro")).thenReturn(Optional.of(plan));
    when(subscriptionRepository.findFirstByBusinessIdOrderByCreatedAtDesc(businessId))
        .thenReturn(Optional.empty());
    when(subscriptionRepository.save(any(Subscription.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(businessRepository.findById(businessId)).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void activatePaidPlan_insideTransaction_notifiesOnlyAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();

    service.activatePaidPlan(businessId, "pro", "monthly");

    verify(checkoutNotificationService, never()).notifyPaid(any(), any(), any(), any());

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    verify(checkoutNotificationService)
        .notifyPaid(eq(businessId), eq("Pro"), eq("monthly"), any(LocalDate.class));
  }

  @Test
  void activatePaidPlan_rolledBack_neverNotifies() {
    TransactionSynchronizationManager.initSynchronization();

    service.activatePaidPlan(businessId, "pro", "monthly");
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    verify(checkoutNotificationService, never()).notifyPaid(any(), any(), any(), any());
  }

  @Test
  void activatePaidPlan_withoutTransaction_notifiesImmediately() {
    service.activatePaidPlan(businessId, "pro", "monthly");

    verify(checkoutNotificationService)
        .notifyPaid(eq(businessId), eq("Pro"), eq("monthly"), any(LocalDate.class));
  }
}
