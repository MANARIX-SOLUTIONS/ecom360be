package com.ecom360.tenant.application.service;

import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.supplier.domain.repository.SupplierRepository;
import com.ecom360.tenant.application.dto.SubscriptionUsageResponse;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionUsageService {

  private final SubscriptionService subscriptionService;
  private final BusinessRepository businessRepository;
  private final BusinessUserRepository businessUserRepository;
  private final StoreRepository storeRepository;
  private final ProductRepository productRepository;
  private final ClientRepository clientRepository;
  private final SupplierRepository supplierRepository;
  private final SaleRepository saleRepository;
  private final RolePermissionService permissionService;

  public SubscriptionUsageService(
      SubscriptionService subscriptionService,
      BusinessRepository businessRepository,
      BusinessUserRepository businessUserRepository,
      StoreRepository storeRepository,
      ProductRepository productRepository,
      ClientRepository clientRepository,
      SupplierRepository supplierRepository,
      SaleRepository saleRepository,
      RolePermissionService permissionService) {
    this.subscriptionService = subscriptionService;
    this.businessRepository = businessRepository;
    this.businessUserRepository = businessUserRepository;
    this.storeRepository = storeRepository;
    this.productRepository = productRepository;
    this.clientRepository = clientRepository;
    this.supplierRepository = supplierRepository;
    this.saleRepository = saleRepository;
    this.permissionService = permissionService;
  }

  public SubscriptionUsageResponse getUsage(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      return new SubscriptionUsageResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    permissionService.require(p, Permission.SUBSCRIPTION_READ);
    return computeUsage(p.businessId());
  }

  /** Usage for any business (platform admin). */
  public SubscriptionUsageResponse getUsageForBusiness(UUID businessId, UserPrincipal p) {
    if (p == null || !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Platform admin only");
    }
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    return computeUsage(businessId);
  }

  private SubscriptionUsageResponse computeUsage(UUID businessId) {
    int usersCount = businessUserRepository.findByBusinessIdAndIsActive(businessId, true).size();
    int storesCount = storeRepository.findByBusinessId(businessId).size();
    long productsCount = productRepository.countByBusinessId(businessId);
    long clientsCount = clientRepository.countByBusinessId(businessId);
    long suppliersCount = supplierRepository.countByBusinessId(businessId);

    ZoneId zone = ZoneId.systemDefault();
    LocalDate now = LocalDate.now(zone);
    Instant start = now.withDayOfMonth(1).atStartOfDay(zone).toInstant();
    Instant end = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant();
    long salesThisMonth =
        saleRepository.countByBusinessIdAndCreatedAtBetween(businessId, start, end);

    Optional<Plan> planOpt = subscriptionService.getPlanForBusiness(businessId);
    int usersLimit = planOpt.map(Plan::getMaxUsers).orElse(0);
    int storesLimit = planOpt.map(Plan::getMaxStores).orElse(0);
    int productsLimit = planOpt.map(Plan::getMaxProducts).orElse(0);
    int clientsLimit = planOpt.map(Plan::getMaxClients).orElse(0);
    int suppliersLimit = planOpt.map(Plan::getMaxSuppliers).orElse(0);
    int salesLimit = planOpt.map(Plan::getMaxSalesPerMonth).orElse(0);

    return new SubscriptionUsageResponse(
        usersCount,
        usersLimit,
        storesCount,
        storesLimit,
        (int) productsCount,
        productsLimit,
        (int) clientsCount,
        clientsLimit,
        (int) suppliersCount,
        suppliersLimit,
        salesThisMonth,
        salesLimit);
  }
}
