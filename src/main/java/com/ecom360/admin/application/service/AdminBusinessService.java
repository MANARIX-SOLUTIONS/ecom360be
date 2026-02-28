package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminBusinessResponse;
import com.ecom360.admin.application.dto.AdminCreateBusinessRequest;
import com.ecom360.admin.application.dto.AdminPlanItem;
import com.ecom360.admin.application.dto.AdminUpdateBusinessRequest;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.model.SubscriptionStatus;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  public AdminBusinessService(
      BusinessRepository businessRepository,
      BusinessUserRepository businessUserRepository,
      UserRepository userRepository,
      SubscriptionRepository subscriptionRepository,
      PlanRepository planRepository,
      StoreRepository storeRepository,
      SaleRepository saleRepository,
      SubscriptionService subscriptionService) {
    this.businessRepository = businessRepository;
    this.businessUserRepository = businessUserRepository;
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    this.storeRepository = storeRepository;
    this.saleRepository = saleRepository;
    this.subscriptionService = subscriptionService;
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
    Map<UUID, String> planMap = loadPlans(bizIds);
    Map<UUID, Integer> storesMap = loadStoresCount(bizIds);
    Map<UUID, Long> revenueMap = loadMonthlyRevenue(bizIds);

    return businesses.map(b -> map(b, ownerMap, planMap, storesMap, revenueMap));
  }

  public AdminBusinessResponse getById(UUID businessId, UserPrincipal p) {
    Business b =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    List<UUID> bizIds = List.of(b.getId());
    Map<UUID, String> ownerMap = loadOwners(bizIds);
    Map<UUID, String> planMap = loadPlans(bizIds);
    Map<UUID, Integer> storesMap = loadStoresCount(bizIds);
    Map<UUID, Long> revenueMap = loadMonthlyRevenue(bizIds);
    return map(b, ownerMap, planMap, storesMap, revenueMap);
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

    if (req.ownerUserId() != null) {
      User owner =
          userRepository
              .findById(req.ownerUserId())
              .orElseThrow(() -> new ResourceNotFoundException("User", req.ownerUserId()));
      BusinessUser bu = BusinessUser.create(b.getId(), owner.getId(), "proprietaire");
      businessUserRepository.save(bu);
    }

    String plan = (req.planSlug() != null && !req.planSlug().isBlank()) ? req.planSlug().trim() : null;
    if (plan == null || "trial".equalsIgnoreCase(plan)) {
      subscriptionService.createTrialForNewBusiness(b.getId());
    } else {
      createActiveSubscriptionForBusiness(b.getId(), plan, "monthly");
    }

    return getById(b.getId(), p);
  }

  @Transactional
  public AdminBusinessResponse update(UUID businessId, AdminUpdateBusinessRequest req, UserPrincipal p) {
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

  /** Assign or change the plan for a business (platform admin). Creates or replaces active subscription. */
  @Transactional
  public void assignPlan(UUID businessId, String planSlug, String billingCycle, UserPrincipal p) {
    Business b =
        businessRepository
            .findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    String cycle = "yearly".equalsIgnoreCase(billingCycle != null ? billingCycle : "") ? "yearly" : "monthly";
    createActiveSubscriptionForBusiness(b.getId(), planSlug.trim(), cycle);
  }

  private void createActiveSubscriptionForBusiness(UUID businessId, String planSlug, String billingCycle) {
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
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Business", businessId));
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
                businessUserRepository.findByBusinessId(bizId).stream()
                    .filter(bu -> "proprietaire".equalsIgnoreCase(bu.getRole()))
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

  private Map<UUID, String> loadPlans(List<UUID> bizIds) {
    return bizIds.stream()
        .map(
            bizId ->
                subscriptionRepository.findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
                    bizId, List.of("active", "trialing")))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(
            Collectors.toMap(
                Subscription::getBusinessId,
                sub -> {
                  Plan plan = planRepository.findById(sub.getPlanId()).orElse(null);
                  return plan != null ? plan.getName() : "-";
                },
                (a, b) -> a));
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
        b.getTrialEndsAt());
  }
}
