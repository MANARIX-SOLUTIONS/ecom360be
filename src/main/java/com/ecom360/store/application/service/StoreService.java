package com.ecom360.store.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.store.application.dto.StoreRequest;
import com.ecom360.store.application.dto.StoreResponse;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.model.BusinessUserStore;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import com.ecom360.tenant.domain.repository.BusinessUserStoreRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StoreService {
  private final StoreRepository storeRepository;
  private final BusinessUserRepository businessUserRepository;
  private final BusinessUserStoreRepository businessUserStoreRepository;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;

  public StoreService(
      StoreRepository storeRepository,
      BusinessUserRepository businessUserRepository,
      BusinessUserStoreRepository businessUserStoreRepository,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService) {
    this.storeRepository = storeRepository;
    this.businessUserRepository = businessUserRepository;
    this.businessUserStoreRepository = businessUserStoreRepository;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
  }

  public StoreResponse create(StoreRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_CREATE);
    subscriptionService
        .getPlanForBusiness(p.businessId())
        .ifPresent(
            plan -> {
              if (!plan.isUnlimited(plan.getMaxStores())) {
                int count = storeRepository.findByBusinessId(p.businessId()).size();
                if (count >= plan.getMaxStores()) {
                  throw new BusinessRuleException(
                      "Limite du plan atteinte : maximum "
                          + plan.getMaxStores()
                          + " magasin(s). Passez à un plan supérieur.");
                }
              }
            });
    Store s = Store.create(p.businessId(), req.name(), req.address(), req.phone());
    return map(storeRepository.save(s));
  }

  public StoreResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_READ);
    return map(find(id, p));
  }

  public List<StoreResponse> list(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_READ);
    List<Store> all = storeRepository.findByBusinessIdAndIsActive(p.businessId(), true);
    // Si l'employé a des boutiques assignées, ne retourner que celles-là
    Optional<BusinessUser> bu =
        businessUserRepository.findByBusinessIdAndUserId(p.businessId(), p.userId());
    if (bu.isPresent()) {
      Set<UUID> assignedIds =
          businessUserStoreRepository.findByBusinessUserId(bu.get().getId()).stream()
              .map(BusinessUserStore::getStoreId)
              .collect(Collectors.toSet());
      if (!assignedIds.isEmpty()) {
        all = all.stream().filter(s -> assignedIds.contains(s.getId())).toList();
      }
    }
    return all.stream().map(this::map).toList();
  }

  public StoreResponse update(UUID id, StoreRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_UPDATE);
    Store s = find(id, p);
    s.setName(req.name());
    s.setAddress(req.address());
    s.setPhone(req.phone());
    s.setIsActive(req.isActive());
    return map(storeRepository.save(s));
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_DELETE);
    storeRepository.delete(find(id, p));
  }

  private Store find(UUID id, UserPrincipal p) {
    return storeRepository
        .findById(id)
        .filter(s -> s.belongsTo(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", id));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }

  private StoreResponse map(Store s) {
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
}
