package com.ecom360.tenant.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.dto.PlanResponse;
import com.ecom360.tenant.application.dto.SubscriptionResponse;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private static final List<String> ACTIVE_STATUSES = List.of("active", "trialing");
  private static final int TRIAL_DAYS = 30;

  private final SubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final RolePermissionService permissionService;

  public SubscriptionService(
      SubscriptionRepository subscriptionRepository,
      PlanRepository planRepository,
      RolePermissionService permissionService) {
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    this.permissionService = permissionService;
  }

  /** Create trial subscription (Pro plan, 30 days) for new business. */
  @Transactional
  public void createTrialForNewBusiness(UUID businessId) {
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
    sub.setStatus("trialing");
    sub.setCurrentPeriodStart(start);
    sub.setCurrentPeriodEnd(end);
    subscriptionRepository.save(sub);
  }

  public Optional<SubscriptionResponse> getCurrent(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_READ);
    return subscriptionRepository
        .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(p.businessId(), ACTIVE_STATUSES)
        .map(
            sub -> {
              Plan plan =
                  planRepository
                      .findById(sub.getPlanId())
                      .orElseThrow(() -> new ResourceNotFoundException("Plan", sub.getPlanId()));
              return new SubscriptionResponse(
                  sub.getId(),
                  plan.getId(),
                  plan.getSlug(),
                  plan.getName(),
                  sub.getBillingCycle(),
                  sub.getStatus(),
                  sub.getCurrentPeriodStart(),
                  sub.getCurrentPeriodEnd());
            });
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
        .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(businessId, ACTIVE_STATUSES)
        .flatMap(sub -> planRepository.findById(sub.getPlanId()).map(Plan::getSlug))
        .orElse(null);
  }

  /**
   * Get current plan for business. Returns empty if no active subscription (no limits enforced).
   */
  public Optional<Plan> getPlanForBusiness(UUID businessId) {
    if (businessId == null) return Optional.empty();
    return subscriptionRepository
        .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(businessId, ACTIVE_STATUSES)
        .flatMap(sub -> planRepository.findById(sub.getPlanId()));
  }

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

    subscriptionRepository
        .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(p.businessId(), ACTIVE_STATUSES)
        .ifPresent(
            sub -> {
              sub.setStatus("cancelled");
              sub.setCancelledAt(LocalDate.now());
              subscriptionRepository.save(sub);
            });

    Subscription sub = new Subscription();
    sub.setBusinessId(p.businessId());
    sub.setPlanId(plan.getId());
    sub.setBillingCycle(cycle);
    sub.setStatus("active");
    sub.setCurrentPeriodStart(start);
    sub.setCurrentPeriodEnd(end);
    sub = subscriptionRepository.save(sub);
    return new SubscriptionResponse(
        sub.getId(),
        plan.getId(),
        plan.getSlug(),
        plan.getName(),
        cycle,
        sub.getStatus(),
        start,
        end);
  }

  @Transactional
  public void cancelSubscription(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_UPDATE);
    Subscription sub =
        subscriptionRepository
            .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(p.businessId(), ACTIVE_STATUSES)
            .orElseThrow(() -> new ResourceNotFoundException("Subscription", "current"));
    sub.cancel();
    subscriptionRepository.save(sub);
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
        plan.getDataRetentionMonths() != null ? plan.getDataRetentionMonths() : 0);
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }
}
