package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminBusinessResponse;
import com.ecom360.admin.application.dto.AdminBusinessSubscriptionInfo;
import com.ecom360.admin.application.dto.AdminCreateBusinessRequest;
import com.ecom360.admin.application.dto.AdminPlanItem;
import com.ecom360.admin.application.dto.AdminRenewSubscriptionRequest;
import com.ecom360.admin.application.dto.AdminUpdateBusinessRequest;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.BusinessRoleBootstrapService;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.application.service.TenantWelcomeNotificationService;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.model.SubscriptionStatus;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessRoleRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBusinessService {

  private final BusinessRepository businessRepository;
  private final BusinessUserRepository businessUserRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final StoreRepository storeRepository;
  private final SaleRepository saleRepository;
  private final SubscriptionService subscriptionService;
  private final BusinessRoleBootstrapService businessRoleBootstrapService;
  private final BusinessRoleRepository businessRoleRepository;
  private final TenantWelcomeNotificationService tenantWelcomeNotificationService;

  public AdminBusinessService(
      BusinessRepository businessRepository,
      BusinessUserRepository businessUserRepository,
      UserRepository userRepository,
      SubscriptionRepository subscriptionRepository,
      PlanRepository planRepository,
      StoreRepository storeRepository,
      SaleRepository saleRepository,
      SubscriptionService subscriptionService,
      BusinessRoleBootstrapService businessRoleBootstrapService,
      BusinessRoleRepository businessRoleRepository,
      TenantWelcomeNotificationService tenantWelcomeNotificationService) {
    this.businessRepository = businessRepository;
    this.businessUserRepository = businessUserRepository;
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    this.storeRepository = storeRepository;
    this.saleRepository = saleRepository;
    this.subscriptionService = subscriptionService;
    this.businessRoleBootstrapService = businessRoleBootstrapService;
    this.businessRoleRepository = businessRoleRepository;
    this.tenantWelcomeNotificationService = tenantWelcomeNotificationService;
  }

  public Page<AdminBusinessResponse> list(
      UserPrincipal p, int page, int size, String search, String status, String planSlug) {
    int pageSize = Math.min(size, 100);
    Pageable pageable = PageRequest.of(page, pageSize);
    String q = (search != null && !search.isBlank()) ? search.trim() : null;
    String st = (status != null && !status.isBlank()) ? status.trim() : null;
    String plan =
        (planSlug != null && !planSlug.isBlank() && !"all".equalsIgnoreCase(planSlug))
            ? planSlug.trim()
            : null;

    Page<Business> businesses;
    if (q == null && st == null && plan == null) {
      businesses = businessRepository.findAllByOrderByCreatedAtDesc(pageable);
    } else {
      businesses = businessRepository.searchByNameOrOwner(q, st, plan, pageable);
    }

    List<UUID> bizIds = businesses.getContent().stream().map(Business::getId).toList();
    Map<UUID, String> ownerMap = loadOwners(bizIds);
    Map<UUID, Subscription> latestSubs = loadLatestSubscriptions(bizIds);
    Map<UUID, String> planMap = buildPlanNamesForBusinesses(bizIds, latestSubs);
    Map<UUID, AdminBusinessSubscriptionInfo> subscriptionInfoMap =
        buildSubscriptionInfos(latestSubs);
    Map<UUID, Integer> storesMap = loadStoresCount(bizIds);
    Map<UUID, Long> revenueMap = loadMonthlyRevenue(bizIds);

    return businesses.map(
        b -> map(b, ownerMap, planMap, subscriptionInfoMap, storesMap, revenueMap));
  }

  public AdminBusinessResponse getById(UUID businessId, UserPrincipal p) {
    Business b =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    List<UUID> bizIds = List.of(b.getId());
    Map<UUID, String> ownerMap = loadOwners(bizIds);
    Map<UUID, Subscription> latestSubs = loadLatestSubscriptions(bizIds);
    Map<UUID, String> planMap = buildPlanNamesForBusinesses(bizIds, latestSubs);
    Map<UUID, AdminBusinessSubscriptionInfo> subscriptionInfoMap =
        buildSubscriptionInfos(latestSubs);
    Map<UUID, Integer> storesMap = loadStoresCount(bizIds);
    Map<UUID, Long> revenueMap = loadMonthlyRevenue(bizIds);
    return map(b, ownerMap, planMap, subscriptionInfoMap, storesMap, revenueMap);
  }

  @Transactional
  public AdminBusinessResponse create(AdminCreateBusinessRequest req, UserPrincipal p) {
    if (businessRepository.existsByEmail(req.email())) {
      throw new ResourceAlreadyExistsException("Business", req.email());
    }
    Business b = Business.create(req.name(), req.email());
    if (req.phone() != null && !req.phone().isBlank()) {
      b.setPhone(req.phone());
    }
    if (req.address() != null && !req.address().isBlank()) {
      b.setAddress(req.address());
    }
    b = businessRepository.save(b);

    businessRoleBootstrapService.ensureDefaultRolesForBusiness(b.getId());
    if (req.ownerUserId() != null) {
      User owner =
          userRepository
              .findById(req.ownerUserId())
              .orElseThrow(() -> new ResourceNotFoundException("User", req.ownerUserId()));
      BusinessRole admin =
          businessRoleRepository
              .findByBusinessIdAndCode(b.getId(), "PROPRIETAIRE")
              .orElseThrow(() -> new IllegalStateException("Default PROPRIETAIRE role missing"));
      BusinessUser bu = BusinessUser.create(b.getId(), owner.getId(), admin);
      businessUserRepository.save(bu);
    }

    String plan =
        (req.planSlug() != null && !req.planSlug().isBlank()) ? req.planSlug().trim() : null;
    if (plan == null || "trial".equalsIgnoreCase(plan)) {
      subscriptionService.createTrialForNewBusiness(b.getId());
    } else {
      createActiveSubscriptionForBusiness(b.getId(), plan, "monthly");
    }

    if (req.ownerUserId() != null) {
      tenantWelcomeNotificationService.sendWelcomeAfterProvisioning(b.getId(), req.ownerUserId());
    }

    return getById(b.getId(), p);
  }

  @Transactional
  public AdminBusinessResponse update(
      UUID businessId, AdminUpdateBusinessRequest req, UserPrincipal p) {
    Business b =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    if (req.name() != null && !req.name().isBlank()) {
      b.setName(req.name());
    }
    if (req.email() != null && !req.email().isBlank()) {
      if (!req.email().equalsIgnoreCase(b.getEmail())
          && businessRepository.findByEmail(req.email()).isPresent()) {
        throw new ResourceAlreadyExistsException("Business", req.email());
      }
      b.setEmail(req.email());
    }
    if (req.phone() != null) {
      b.setPhone(req.phone());
    }
    if (req.address() != null) {
      b.setAddress(req.address());
    }
    b = businessRepository.save(b);
    return getById(b.getId(), p);
  }

  /**
   * Renouvelle l'abonnement : une période supplémentaire (mensuelle ou annuelle). Si l'abonnement
   * payant est encore actif, la nouvelle période commence à la fin de la période courante. Sinon
   * (expiré, annulé, essai) la période commence aujourd'hui ; l'essai est converti en payant dès
   * aujourd'hui.
   */
  @Transactional
  public void renewSubscription(
      UUID businessId, AdminRenewSubscriptionRequest req, UserPrincipal p) {
    Objects.requireNonNull(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));

    Subscription latest =
        subscriptionRepository.findFirstByBusinessIdOrderByCreatedAtDesc(businessId).orElse(null);

    String planSlug;
    String billingCycleRaw;
    if (req != null && req.planSlug() != null && !req.planSlug().isBlank()) {
      planSlug = req.planSlug().trim();
      billingCycleRaw =
          req.billingCycle() != null && !req.billingCycle().isBlank()
              ? req.billingCycle().trim()
              : "monthly";
    } else if (latest != null) {
      Plan lp =
          planRepository
              .findById(latest.getPlanId())
              .orElseThrow(() -> new ResourceNotFoundException("Plan", latest.getPlanId()));
      planSlug = lp.getSlug();
      billingCycleRaw =
          req != null && req.billingCycle() != null && !req.billingCycle().isBlank()
              ? req.billingCycle().trim()
              : latest.getBillingCycle();
    } else {
      throw new BusinessRuleException(
          "Aucun abonnement existant — précisez un plan ou utilisez « Changer le plan ».");
    }

    Plan targetPlan =
        planRepository
            .findBySlug(planSlug)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planSlug));
    if (!Boolean.TRUE.equals(targetPlan.getIsActive())) {
      throw new IllegalArgumentException("Plan is not active: " + planSlug);
    }

    int storeCount = storeRepository.findByBusinessId(businessId).size();
    if (!targetPlan.isUnlimited(targetPlan.getMaxStores())
        && storeCount > targetPlan.getMaxStores()) {
      throw new BusinessRuleException(
          "Cette entreprise compte "
              + storeCount
              + " magasin(s). Le plan « "
              + targetPlan.getName()
              + " » autorise au maximum "
              + targetPlan.getMaxStores()
              + " magasin(s). Réduisez le nombre de boutiques ou choisissez un plan supérieur.");
    }

    String cycle =
        "yearly".equalsIgnoreCase(billingCycleRaw != null ? billingCycleRaw : "")
            ? "yearly"
            : "monthly";
    LocalDate today = LocalDate.now();
    LocalDate anchor;
    if (latest == null) {
      anchor = today;
    } else if (latest.isTrialing()) {
      anchor = today;
    } else if (SubscriptionStatus.ACTIVE.equals(latest.getStatus())) {
      LocalDate periodEnd = latest.getCurrentPeriodEnd();
      anchor = periodEnd.isBefore(today) ? today : periodEnd;
    } else {
      anchor = today;
    }

    if (latest != null && SubscriptionStatus.ACCESS_GRANTING.contains(latest.getStatus())) {
      latest.cancelImmediate();
      subscriptionRepository.save(latest);
    }

    LocalDate periodEnd = "yearly".equals(cycle) ? anchor.plusYears(1) : anchor.plusMonths(1);
    Subscription sub = new Subscription();
    sub.setBusinessId(businessId);
    sub.setPlanId(targetPlan.getId());
    sub.setBillingCycle(cycle);
    sub.setStatus(SubscriptionStatus.ACTIVE);
    sub.setCurrentPeriodStart(anchor);
    sub.setCurrentPeriodEnd(periodEnd);
    subscriptionRepository.save(sub);

    businessRepository
        .findById(businessId)
        .ifPresent(
            biz -> {
              biz.activate();
              biz.setTrialEndsAt(null);
              biz.setTrialUsedAt(Instant.now());
              businessRepository.save(biz);
            });
  }

  /**
   * Assign or change the plan for a business (platform admin). Creates or replaces active
   * subscription.
   */
  @Transactional
  public void assignPlan(UUID businessId, String planSlug, String billingCycle, UserPrincipal p) {
    Business b =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    Plan targetPlan =
        planRepository
            .findBySlug(planSlug.trim())
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planSlug));
    if (!Boolean.TRUE.equals(targetPlan.getIsActive())) {
      throw new IllegalArgumentException("Plan is not active: " + planSlug);
    }
    int storeCount = storeRepository.findByBusinessId(businessId).size();
    if (!targetPlan.isUnlimited(targetPlan.getMaxStores())
        && storeCount > targetPlan.getMaxStores()) {
      throw new BusinessRuleException(
          "Cette entreprise compte "
              + storeCount
              + " magasin(s). Le plan « "
              + targetPlan.getName()
              + " » autorise au maximum "
              + targetPlan.getMaxStores()
              + " magasin(s). Réduisez le nombre de boutiques ou choisissez un plan supérieur.");
    }
    String cycle =
        "yearly".equalsIgnoreCase(billingCycle != null ? billingCycle : "") ? "yearly" : "monthly";
    createActiveSubscriptionForBusiness(b.getId(), planSlug.trim(), cycle);
  }

  private void createActiveSubscriptionForBusiness(
      UUID businessId, String planSlug, String billingCycle) {
    Plan plan =
        planRepository
            .findBySlug(planSlug)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planSlug));
    if (!Boolean.TRUE.equals(plan.getIsActive())) {
      throw new IllegalArgumentException("Plan is not active: " + planSlug);
    }
    Optional<Subscription> currentOpt =
        subscriptionRepository.findFirstByBusinessIdOrderByCreatedAtDesc(businessId);
    if (currentOpt.isPresent()) {
      Subscription current = currentOpt.get();
      if (SubscriptionStatus.ACCESS_GRANTING.contains(current.getStatus())) {
        current.cancelImmediate();
        subscriptionRepository.save(current);
      }
    }
    LocalDate start = LocalDate.now();
    LocalDate end = "yearly".equals(billingCycle) ? start.plusYears(1) : start.plusMonths(1);
    Subscription sub = new Subscription();
    sub.setBusinessId(businessId);
    sub.setPlanId(plan.getId());
    sub.setBillingCycle(billingCycle);
    sub.setStatus(SubscriptionStatus.ACTIVE);
    sub.setCurrentPeriodStart(start);
    sub.setCurrentPeriodEnd(end);
    subscriptionRepository.save(sub);

    businessRepository
        .findById(businessId)
        .ifPresent(
            biz -> {
              biz.activate();
              biz.setTrialEndsAt(null);
              biz.setTrialUsedAt(Instant.now());
              businessRepository.save(biz);
            });
  }

  public List<AdminPlanItem> listPlansForAdmin(UserPrincipal p) {
    return planRepository.findByIsActiveTrueOrderByPriceMonthlyAsc().stream()
        .map(
            plan ->
                new AdminPlanItem(
                    plan.getId(),
                    plan.getSlug(),
                    plan.getName(),
                    plan.getPriceMonthly(),
                    plan.getPriceYearly()))
        .toList();
  }

  public void setStatus(UUID businessId, String status, UserPrincipal p) {
    Business b =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    if ("suspended".equals(status)) {
      b.suspend();
    } else if ("active".equals(status)) {
      b.activate();
    } else {
      throw new IllegalArgumentException("Invalid status: " + status);
    }
    businessRepository.save(b);
  }

  private Map<UUID, String> loadOwners(List<UUID> bizIds) {
    return bizIds.stream()
        .flatMap(
            bizId ->
                businessUserRepository.findByBusinessIdOrderByCreatedAtWithRole(bizId).stream()
                    .filter(bu -> "PROPRIETAIRE".equalsIgnoreCase(bu.getBusinessRole().getCode()))
                    .findFirst()
                    .stream())
        .collect(
            Collectors.toMap(
                BusinessUser::getBusinessId,
                bu -> {
                  User u = userRepository.findById(bu.getUserId()).orElse(null);
                  return u != null ? u.getFullName() : "-";
                },
                (a, b) -> a));
  }

  /** Dernier abonnement créé par entreprise (tous statuts). */
  private Map<UUID, Subscription> loadLatestSubscriptions(List<UUID> bizIds) {
    if (bizIds.isEmpty()) {
      return Map.of();
    }
    List<Subscription> all = subscriptionRepository.findByBusinessIdIn(bizIds);
    return all.stream()
        .collect(
            Collectors.toMap(
                Subscription::getBusinessId,
                s -> s,
                (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
  }

  private Map<UUID, String> buildPlanNamesForBusinesses(
      List<UUID> bizIds, Map<UUID, Subscription> latestSubs) {
    Map<UUID, String> planMap = new HashMap<>();
    for (UUID id : bizIds) {
      Subscription s = latestSubs.get(id);
      planMap.put(id, s == null ? "-" : planNameForSubscription(s));
    }
    return planMap;
  }

  private String planNameForSubscription(Subscription sub) {
    return planRepository.findById(sub.getPlanId()).map(Plan::getName).orElse("-");
  }

  private Map<UUID, AdminBusinessSubscriptionInfo> buildSubscriptionInfos(
      Map<UUID, Subscription> latestSubs) {
    Map<UUID, AdminBusinessSubscriptionInfo> m = new HashMap<>();
    for (Map.Entry<UUID, Subscription> e : latestSubs.entrySet()) {
      m.put(e.getKey(), toSubscriptionInfo(e.getValue()));
    }
    return m;
  }

  private AdminBusinessSubscriptionInfo toSubscriptionInfo(Subscription sub) {
    Plan plan =
        planRepository
            .findById(sub.getPlanId())
            .orElseThrow(() -> new ResourceNotFoundException("Plan", sub.getPlanId()));
    LocalDate today = LocalDate.now();
    long daysRemaining = ChronoUnit.DAYS.between(today, sub.getCurrentPeriodEnd());
    if (daysRemaining < 0) {
      daysRemaining = 0;
    }
    return new AdminBusinessSubscriptionInfo(
        plan.getSlug(),
        plan.getName(),
        sub.getBillingCycle(),
        sub.getStatus(),
        sub.getCurrentPeriodStart(),
        sub.getCurrentPeriodEnd(),
        daysRemaining,
        Boolean.TRUE.equals(sub.getCancelAtPeriodEnd()),
        sub.isTrialing());
  }

  private Map<UUID, Integer> loadStoresCount(List<UUID> bizIds) {
    return bizIds.stream()
        .collect(Collectors.toMap(id -> id, id -> storeRepository.findByBusinessId(id).size()));
  }

  private Map<UUID, Long> loadMonthlyRevenue(List<UUID> bizIds) {
    if (bizIds.isEmpty()) return Map.of();
    LocalDate now = LocalDate.now();
    Instant monthStart = now.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant monthEnd = now.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    List<Object[]> rows = saleRepository.sumTotalByBusinessIdBetween(monthStart, monthEnd);
    return rows.stream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));
  }

  private static String formatRevenue(long amount) {
    return String.format(Locale.FRANCE, "%,d F", amount);
  }

  private AdminBusinessResponse map(
      Business b,
      Map<UUID, String> ownerMap,
      Map<UUID, String> planMap,
      Map<UUID, AdminBusinessSubscriptionInfo> subscriptionInfoMap,
      Map<UUID, Integer> storesMap,
      Map<UUID, Long> revenueMap) {
    long revenue = revenueMap.getOrDefault(b.getId(), 0L);
    return new AdminBusinessResponse(
        b.getId(),
        b.getName(),
        ownerMap.getOrDefault(b.getId(), "-"),
        b.getEmail(),
        b.getPhone() != null ? b.getPhone() : "",
        b.getAddress() != null ? b.getAddress() : "",
        planMap.getOrDefault(b.getId(), "-"),
        b.getStatus(),
        storesMap.getOrDefault(b.getId(), 0),
        formatRevenue(revenue),
        b.getCreatedAt(),
        b.getTrialEndsAt(),
        subscriptionInfoMap.get(b.getId()));
  }
}
