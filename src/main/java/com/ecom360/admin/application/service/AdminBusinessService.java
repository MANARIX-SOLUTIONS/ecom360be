package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminBusinessResponse;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminBusinessService {

  private final BusinessRepository businessRepository;
  private final BusinessUserRepository businessUserRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final StoreRepository storeRepository;
  private final SaleRepository saleRepository;

  public AdminBusinessService(
      BusinessRepository businessRepository,
      BusinessUserRepository businessUserRepository,
      UserRepository userRepository,
      SubscriptionRepository subscriptionRepository,
      PlanRepository planRepository,
      StoreRepository storeRepository,
      SaleRepository saleRepository) {
    this.businessRepository = businessRepository;
    this.businessUserRepository = businessUserRepository;
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    this.storeRepository = storeRepository;
    this.saleRepository = saleRepository;
  }

  public Page<AdminBusinessResponse> list(
      UserPrincipal p, int page, int size, String search, String status, String planSlug) {
    Pageable pageable =
        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
    String q = (search != null && !search.isBlank()) ? search.trim() : null;
    String st = (status != null && !status.isBlank()) ? status.trim() : null;
    String plan =
        (planSlug != null && !planSlug.isBlank() && !"all".equalsIgnoreCase(planSlug))
            ? planSlug.trim()
            : null;
    Page<Business> businesses = businessRepository.searchByNameOrOwner(q, st, plan, pageable);

    List<UUID> bizIds = businesses.getContent().stream().map(Business::getId).toList();
    Map<UUID, String> ownerMap = loadOwners(bizIds);
    Map<UUID, String> planMap = loadPlans(bizIds);
    Map<UUID, Integer> storesMap = loadStoresCount(bizIds);
    Map<UUID, Long> revenueMap = loadMonthlyRevenue(bizIds);

    return businesses.map(b -> map(b, ownerMap, planMap, storesMap, revenueMap));
  }

  public void setStatus(UUID businessId, String status, UserPrincipal p) {
    Business b =
        businessRepository
            .findById(businessId)
            .orElseThrow(
                () ->
                    new com.ecom360.shared.domain.exception.ResourceNotFoundException(
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
