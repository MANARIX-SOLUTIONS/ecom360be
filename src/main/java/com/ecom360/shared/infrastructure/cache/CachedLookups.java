package com.ecom360.shared.infrastructure.cache;

import com.ecom360.catalog.application.dto.CategoryResponse;
import com.ecom360.catalog.domain.model.Category;
import com.ecom360.catalog.domain.repository.CategoryRepository;
import com.ecom360.store.application.dto.StoreResponse;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.dto.PlanResponse;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.BusinessUserStore;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.BusinessUserStoreRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cacheable reads isolated from auth checks. Callers must authorize before
 * invoking these methods
 * so Spring AOP cache hits never skip permission enforcement.
 */
@Component
public class CachedLookups {

  private final CategoryRepository categoryRepo;
  private final PlanRepository planRepo;
  private final StoreRepository storeRepo;
  private final BusinessUserRepository businessUserRepo;
  private final BusinessUserStoreRepository businessUserStoreRepo;

  public CachedLookups(
      CategoryRepository categoryRepo,
      PlanRepository planRepo,
      StoreRepository storeRepo,
      BusinessUserRepository businessUserRepo,
      BusinessUserStoreRepository businessUserStoreRepo) {
    this.categoryRepo = categoryRepo;
    this.planRepo = planRepo;
    this.storeRepo = storeRepo;
    this.businessUserRepo = businessUserRepo;
    this.businessUserStoreRepo = businessUserStoreRepo;
  }

  @Cacheable(value = "categories", key = "#businessId")
  public List<CategoryResponse> categoriesByBusiness(UUID businessId) {
    return categoryRepo.findByBusinessIdOrderBySortOrderAsc(businessId).stream()
        .map(this::mapCategory)
        .toList();
  }

  @CacheEvict(value = "categories", key = "#businessId")
  public void evictCategories(UUID businessId) {
    // eviction marker
  }

  @Cacheable(value = "plans", key = "'active'")
  public List<PlanResponse> activePlans() {
    return planRepo.findByIsActiveTrueOrderByPriceMonthlyAsc().stream()
        .map(this::mapPlan)
        .toList();
  }

  @Cacheable(value = "stores", key = "#businessId + ':' + #userId")
  public List<StoreResponse> storesForUser(UUID businessId, UUID userId) {
    List<Store> all = storeRepo.findByBusinessIdAndIsActive(businessId, true);
    Optional<BusinessUser> bu = businessUserRepo.findByBusinessIdAndUserId(businessId, userId);
    if (bu.isPresent()) {
      Set<UUID> assignedIds = businessUserStoreRepo.findByBusinessUserId(bu.get().getId()).stream()
          .map(BusinessUserStore::getStoreId)
          .collect(Collectors.toSet());
      if (!assignedIds.isEmpty()) {
        all = all.stream().filter(s -> assignedIds.contains(s.getId())).toList();
      }
    }
    return all.stream().map(this::mapStore).toList();
  }

  @CacheEvict(value = "stores", allEntries = true)
  public void evictAllStores() {
    // eviction marker
  }

  private CategoryResponse mapCategory(Category c) {
    return new CategoryResponse(
        c.getId(),
        c.getBusinessId(),
        c.getParentId(),
        c.getName(),
        c.getColor(),
        c.getSortOrder(),
        c.getCreatedAt());
  }

  private StoreResponse mapStore(Store s) {
    return new StoreResponse(
        s.getId(),
        s.getBusinessId(),
        s.getName(),
        s.getAddress(),
        s.getPhone(),
        s.getIsActive(),
        s.getCreatedAt(),
        s.getUpdatedAt());
  }

  private PlanResponse mapPlan(Plan plan) {
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
}
