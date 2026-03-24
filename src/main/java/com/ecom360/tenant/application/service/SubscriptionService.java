package com.ecom360.tenant.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.PlanResponse;
import com.ecom360.tenant.application.dto.SubscriptionResponse;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.model.SubscriptionStatus;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private static final int TRIAL_DAYS = 30;

  private final SubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final BusinessRepository businessRepository;
  private final RolePermissionService permissionService;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      PlanRepository planRepository,
      BusinessRepository businessRepository,
      RolePermissionService permissionService) {
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    this.businessRepository = businessRepository;
    this.permissionService = permissionService;
  }

  /** Create trial subscription for new business. Trial allowed only once per business. */
  @Transactional
  public void createTrialForNewBusiness(UUID businessId) {
    Business business =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    if (business.hasUsedTrial()) {
      throw new BusinessRuleException(
          "L'essai gratuit n'est utilisable qu'une seule fois. Veuillez souscrire à un plan.");
    }

    Plan proPlan =
        planRepository
            .findBySlug("pro")
            .orElse(
                planRepository.findByIsActiveTrueOrderByPriceMonthlyAsc().stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Plan", "any")));
    LocalDate start = LocalDate.now();
    LocalDate end = start.plusDays(TRIAL_DAYS);

    Subscription sub = new Subscription();
    sub.setBusinessId(businessId);
    sub.setPlanId(proPlan.getId());
    sub.setBillingCycle("monthly");
    sub.setStatus(SubscriptionStatus.TRIALING);
    sub.setCurrentPeriodStart(start);
    sub.setCurrentPeriodEnd(end);
    subscriptionRepository.save(sub);

    businessRepository
        .findById(businessId)
        .ifPresent(
            biz -> {
              biz.setTrialEndsAt(end);
              biz.setStatus("trial");
              businessRepository.save(biz);
            });
  }

  public Optional<SubscriptionResponse> getCurrent(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_READ);
    return subscriptionRepository
        .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
            p.businessId(), SubscriptionStatus.ACCESS_GRANTING)
        .filter(this::notExpiredOrExpireLazy)
        .map(this::toSubscriptionResponse);
  }

  public List<PlanResponse> listPlans(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_READ);
    return planRepository.findByIsActiveTrueOrderByPriceMonthlyAsc().stream()
        .map(this::toPlanResponse)
        .toList();
  }

  /** Get plan slug for business (used at login). Returns null if no active subscription. */
  public String getPlanSlugForBusiness(UUID businessId) {
    if (businessId == null) return null;
    return subscriptionRepository
        .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
            businessId, SubscriptionStatus.ACCESS_GRANTING)
        .filter(this::notExpiredOrExpireLazy)
        .flatMap(sub -> planRepository.findById(sub.getPlanId()).map(Plan::getSlug))
        .orElse(null);
  }

  /**
   * Get current plan for business. Returns empty if no active subscription (no limits enforced).
   * Lazy-expires subscriptions past period end when detected.
   */
  public Optional<Plan> getPlanForBusiness(UUID businessId) {
    if (businessId == null) return Optional.empty();
    return subscriptionRepository
        .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
            businessId, SubscriptionStatus.ACCESS_GRANTING)
        .filter(this::notExpiredOrExpireLazy)
        .flatMap(sub -> planRepository.findById(sub.getPlanId()));
  }

  /**
   * Enforces {@code max_stores} from the current plan when adding a store. No-op when no
   * access-granting subscription exists (same behaviour as previous inline checks).
   */
  public void assertCanAddStore(UUID businessId, int currentStoreCount) {
    getPlanForBusiness(businessId)
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxStores())) {
                if (currentStoreCount >= plan.getMaxStores()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxStores()
                          + " magasin(s). Passez à un plan supérieur.");
                }
              }
            });
  }

  public LocalDate clampPeriodStartToRetention(UUID businessId, LocalDate periodStart) {
    return getPlanForBusiness(businessId)
        .map(
            plan -> {
              int m = plan.getDataRetentionMonths() == null ? 0 : plan.getDataRetentionMonths();
              if (m <= 0) return periodStart;
              LocalDate min = LocalDate.now(ZoneId.systemDefault()).minusMonths(m);
              return periodStart.isBefore(min) ? min : periodStart;
            })
        .orElse(periodStart);
  }

  /** True if instant is strictly before the plan's retained history window. */
  public boolean isBeforeDataRetention(UUID businessId, Instant at) {
    Optional<Plan> opt = getPlanForBusiness(businessId);
    if (opt.isEmpty()) return false;
    int m = opt.get().getDataRetentionMonths() == null ? 0 : opt.get().getDataRetentionMonths();
    if (m <= 0) return false;
    LocalDate min = LocalDate.now(ZoneId.systemDefault()).minusMonths(m);
    return at.isBefore(min.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  public Instant clampSaleHistoryFrom(UUID businessId, Instant requestedFrom) {
    Optional<Plan> opt = getPlanForBusiness(businessId);
    if (opt.isEmpty()) return requestedFrom;
    int m = opt.get().getDataRetentionMonths() == null ? 0 : opt.get().getDataRetentionMonths();
    if (m <= 0) return requestedFrom;
    Instant minI =
        LocalDate.now(ZoneId.systemDefault())
            .minusMonths(m)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant();
    if (requestedFrom == null || requestedFrom.isBefore(minI)) return minI;
    return requestedFrom;
  }

  public void requireFeatureReports(UUID businessId) {
    getPlanForBusiness(businessId)
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureReports())) {
                throw new AccessDeniedException(
                    "Rapports et analyses non inclus dans le plan Starter. Passez au plan Pro.");
              }
            });
  }

  public void requireFeatureApi(UUID businessId) {
    getPlanForBusiness(businessId)
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureApi())) {
                throw new AccessDeniedException(
                    "API, clés d'accès et webhooks sont réservés au plan Business.");
              }
            });
  }

  public void requireSupplierTracking(UUID businessId) {
    getPlanForBusiness(businessId)
        .ifPresent(
            plan -> {
              if (!Boolean.TRUE.equals(plan.getFeatureSupplierTracking())) {
                throw new AccessDeniedException(
                    "Commandes et suivi fournisseurs avancé non inclus dans votre plan. Passez au plan Pro.");
              }
            });
  }

  /**
   * Subscribe or change plan. Handles: trial→paid, expired→paid, active→upgrade/downgrade,
   * cancelled→reactivate.
   */
  @Transactional
  public SubscriptionResponse changePlan(String planSlug, String billingCycle, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_UPDATE);

    Plan plan =
        planRepository
            .findBySlug(planSlug)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planSlug));
    if (!Boolean.TRUE.equals(plan.getIsActive())) {
      throw new BusinessRuleException("Plan is not available");
    }

    String cycle = "yearly".equalsIgnoreCase(billingCycle) ? "yearly" : "monthly";
    LocalDate start = LocalDate.now();
    LocalDate end = cycle.equals("yearly") ? start.plusYears(1) : start.plusMonths(1);

    Optional<Subscription> currentOpt =
        subscriptionRepository.findFirstByBusinessIdOrderByCreatedAtDesc(p.businessId());

    if (currentOpt.isPresent()) {
      Subscription current = currentOpt.get();
      if (current.isActive()) {
        if (current.getPlanId().equals(plan.getId()) && cycle.equals(current.getBillingCycle())) {
          throw new BusinessRuleException("Already on this plan and billing cycle");
        }
        current.cancelImmediate();
        subscriptionRepository.save(current);
      }
    }

    Subscription sub = new Subscription();
    sub.setBusinessId(p.businessId());
    sub.setPlanId(plan.getId());
    sub.setBillingCycle(cycle);
    sub.setStatus(SubscriptionStatus.ACTIVE);
    sub.setCurrentPeriodStart(start);
    sub.setCurrentPeriodEnd(end);
    sub = subscriptionRepository.save(sub);

    businessRepository
        .findById(p.businessId())
        .ifPresent(
            biz -> {
              biz.activate();
              biz.setTrialEndsAt(null);
              biz.setTrialUsedAt(Instant.now());
              businessRepository.save(biz);
            });

    return toSubscriptionResponse(sub);
  }

  /**
   * Cancel subscription. By default cancels at period end (keeps access until then). Use
   * cancelImmediate for instant cancellation.
   */
  @Transactional
  public void cancelSubscription(boolean atPeriodEnd, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_UPDATE);
    Subscription sub =
        subscriptionRepository
            .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
                p.businessId(), SubscriptionStatus.ACCESS_GRANTING)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", "current"));

    if (atPeriodEnd) {
      sub.cancelAtPeriodEnd();
      subscriptionRepository.save(sub);
    } else {
      sub.cancelImmediate();
      subscriptionRepository.save(sub);
    }
  }

  /** Reactivate a cancelled subscription (before period end). */
  @Transactional
  public SubscriptionResponse reactivateSubscription(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_UPDATE);
    Subscription sub =
        subscriptionRepository
            .findFirstByBusinessIdOrderByCreatedAtDesc(p.businessId())
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", "current"));

    if (!sub.getCancelAtPeriodEnd()) {
      throw new BusinessRuleException("No subscription to reactivate");
    }
    if (LocalDate.now().isAfter(sub.getCurrentPeriodEnd())) {
      throw new BusinessRuleException("Subscription period has ended");
    }

    sub.setCancelAtPeriodEnd(false);
    subscriptionRepository.save(sub);
    Plan plan =
        planRepository
            .findById(sub.getPlanId())
            .orElseThrow(() -> new ResourceNotFoundException("Plan", sub.getPlanId()));
    return toSubscriptionResponse(sub);
  }

  private SubscriptionResponse toSubscriptionResponse(Subscription sub) {
    Plan plan =
        planRepository
            .findById(sub.getPlanId())
            .orElseThrow(() -> new ResourceNotFoundException("Plan", sub.getPlanId()));
    long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), sub.getCurrentPeriodEnd());
    return new SubscriptionResponse(
        sub.getId(),
        plan.getId(),
        plan.getSlug(),
        plan.getName(),
        sub.getBillingCycle(),
        sub.getStatus(),
        sub.getCurrentPeriodStart(),
        sub.getCurrentPeriodEnd(),
        sub.getCancelAtPeriodEnd(),
        Math.max(0, daysRemaining),
        sub.isTrialing());
  }

  private PlanResponse toPlanResponse(Plan plan) {
    return new PlanResponse(
        plan.getId(),
        plan.getSlug(),
        plan.getName(),
        plan.getPriceMonthly(),
        plan.getPriceYearly(),
        plan.getMaxUsers(),
        plan.getMaxStores(),
        plan.getMaxProducts(),
        plan.getMaxSalesPerMonth(),
        plan.getMaxClients(),
        plan.getMaxSuppliers(),
        Boolean.TRUE.equals(plan.getFeatureExpenses()),
        Boolean.TRUE.equals(plan.getFeatureReports()),
        Boolean.TRUE.equals(plan.getFeatureAdvancedReports()),
        Boolean.TRUE.equals(plan.getFeatureMultiPayment()),
        Boolean.TRUE.equals(plan.getFeatureExportPdf()),
        Boolean.TRUE.equals(plan.getFeatureExportExcel()),
        Boolean.TRUE.equals(plan.getFeatureClientCredits()),
        Boolean.TRUE.equals(plan.getFeatureSupplierTracking()),
        Boolean.TRUE.equals(plan.getFeatureRoleManagement()),
        Boolean.TRUE.equals(plan.getFeatureApi()),
        Boolean.TRUE.equals(plan.getFeatureCustomBranding()),
        Boolean.TRUE.equals(plan.getFeaturePrioritySupport()),
        Boolean.TRUE.equals(plan.getFeatureAccountManager()),
        Boolean.TRUE.equals(plan.getFeatureStockAlerts()),
        Boolean.TRUE.equals(plan.getFeatureDeliveryCouriers()),
        Boolean.TRUE.equals(plan.getFeatureGlobalView()),
        plan.getDataRetentionMonths() != null ? plan.getDataRetentionMonths() : 0);
  }

  /**
   * Lazy expiration: if subscription period has ended, expire it and return false. Otherwise return
   * true. Runs in its own transaction when a write is needed.
   */
  @Transactional
  public boolean notExpiredOrExpireLazy(Subscription sub) {
    if (sub.getCurrentPeriodEnd().isBefore(LocalDate.now())) {
      if (sub.getCancelAtPeriodEnd()) {
        sub.markCancelled();
      } else {
        sub.expire();
      }
      subscriptionRepository.save(sub);
      if (sub.isTrialing()) {
        businessRepository
            .findById(sub.getBusinessId())
            .ifPresent(
                biz -> {
                  if ("trial".equals(biz.getStatus()) || "active".equals(biz.getStatus())) {
                    biz.setStatus("expired");
                    biz.setTrialUsedAt(Instant.now());
                    businessRepository.save(biz);
                  }
                });
      }
      return false;
    }
    return true;
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }
}
