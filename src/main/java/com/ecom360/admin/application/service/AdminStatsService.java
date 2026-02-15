package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminStatsResponse;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import com.ecom360.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminStatsService {

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final SaleRepository saleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final BusinessUserRepository businessUserRepository;

    public AdminStatsService(BusinessRepository businessRepository,
                             UserRepository userRepository,
                             StoreRepository storeRepository,
                             SaleRepository saleRepository,
                             SubscriptionRepository subscriptionRepository,
                             PlanRepository planRepository,
                             BusinessUserRepository businessUserRepository) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.saleRepository = saleRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.businessUserRepository = businessUserRepository;
    }

    public AdminStatsResponse getStats(UserPrincipal p) {
        long businessesCount = businessRepository.count();
        long usersCount = userRepository.count();
        long storesCount = storeRepository.count();

        LocalDate now = LocalDate.now();
        Instant monthStart = now.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant monthEnd = now.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Object[]> revenueByBusiness = saleRepository.sumTotalByBusinessIdBetween(monthStart, monthEnd);
        long monthlyRevenue = revenueByBusiness.stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();

        Map<UUID, Long> revenueMap = revenueByBusiness.stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));

        List<AdminStatsResponse.PlanCount> planDistribution = computePlanDistribution((int) businessesCount);
        List<AdminStatsResponse.TopBusiness> topBusinesses = computeTopBusinesses(revenueMap);

        return new AdminStatsResponse(
                businessesCount,
                usersCount,
                storesCount,
                monthlyRevenue,
                planDistribution,
                topBusinesses
        );
    }

    private List<AdminStatsResponse.PlanCount> computePlanDistribution(int totalBusinesses) {
        if (totalBusinesses == 0) {
            return List.of();
        }
        List<Subscription> active = subscriptionRepository.findAll().stream()
                .filter(Subscription::isActive)
                .toList();
        Map<String, Integer> byPlan = new HashMap<>();
        for (Subscription sub : active) {
            Plan plan = planRepository.findById(sub.getPlanId()).orElse(null);
            String name = plan != null ? plan.getName() : "—";
            byPlan.merge(name, 1, Integer::sum);
        }
        return byPlan.entrySet().stream()
                .map(e -> {
                    int pct = (int) Math.round(100.0 * e.getValue() / totalBusinesses);
                    return new AdminStatsResponse.PlanCount(e.getKey(), e.getValue(), pct);
                })
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .toList();
    }

    private List<AdminStatsResponse.TopBusiness> computeTopBusinesses(Map<UUID, Long> revenueMap) {
        List<UUID> topBizIds = revenueMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
        if (topBizIds.isEmpty()) {
            List<Business> recent = businessRepository.findAllByOrderByCreatedAtDesc(
                    org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
            topBizIds = recent.stream().map(Business::getId).toList();
        }

        Map<UUID, String> ownerMap = loadOwners(topBizIds);
        Map<UUID, String> planMap = loadPlans(topBizIds);
        Map<UUID, Integer> storesMap = topBizIds.stream()
                .collect(Collectors.toMap(id -> id, id -> storeRepository.findByBusinessId(id).size()));

        List<AdminStatsResponse.TopBusiness> result = new ArrayList<>();
        for (UUID bizId : topBizIds) {
            Business b = businessRepository.findById(bizId).orElse(null);
            if (b == null) continue;
            long revenue = revenueMap.getOrDefault(bizId, 0L);
            result.add(new AdminStatsResponse.TopBusiness(
                    b.getName(),
                    ownerMap.getOrDefault(bizId, "—"),
                    revenue,
                    storesMap.getOrDefault(bizId, 0),
                    planMap.getOrDefault(bizId, "—")
            ));
        }
        return result;
    }

    private Map<UUID, String> loadOwners(List<UUID> bizIds) {
        return bizIds.stream()
                .flatMap(bizId -> businessUserRepository.findByBusinessId(bizId).stream()
                        .filter(bu -> "proprietaire".equalsIgnoreCase(bu.getRole()))
                        .findFirst()
                        .stream())
                .collect(Collectors.toMap(BusinessUser::getBusinessId, bu -> {
                    User u = userRepository.findById(bu.getUserId()).orElse(null);
                    return u != null ? u.getFullName() : "—";
                }, (a, b) -> a));
    }

    private Map<UUID, String> loadPlans(List<UUID> bizIds) {
        return bizIds.stream()
                .map(bizId -> subscriptionRepository.findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
                        bizId, List.of("active", "trialing")))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Subscription::getBusinessId, sub -> {
                    Plan plan = planRepository.findById(sub.getPlanId()).orElse(null);
                    return plan != null ? plan.getName() : "—";
                }, (a, b) -> a));
    }
}
