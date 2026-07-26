package com.ecom360.store.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.shared.infrastructure.cache.CachedLookups;
import com.ecom360.store.application.dto.StoreRequest;
import com.ecom360.store.application.dto.StoreResponse;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StoreService {
  private final StoreRepository storeRepository;
  private final SubscriptionService subscriptionService;
  private final RolePermissionService permissionService;
  private final CachedLookups cachedLookups;

  public StoreService(
      StoreRepository storeRepository,
      SubscriptionService subscriptionService,
      RolePermissionService permissionService,
      CachedLookups cachedLookups) {
    this.storeRepository = storeRepository;
    this.subscriptionService = subscriptionService;
    this.permissionService = permissionService;
    this.cachedLookups = cachedLookups;
  }

  public StoreResponse create(StoreRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_CREATE);
    subscriptionService.assertCanAddStore(
        p.businessId(), storeRepository.findByBusinessId(p.businessId()).size());
    Store s = Store.create(p.businessId(), req.name(), req.address(), req.phone());
    StoreResponse created = map(storeRepository.save(s));
    cachedLookups.evictAllStores();
    return created;
  }

  public StoreResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_READ);
    return map(find(id, p));
  }

  public List<StoreResponse> list(UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_READ);
    return cachedLookups.storesForUser(p.businessId(), p.userId());
  }

  public StoreResponse update(UUID id, StoreRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_UPDATE);
    Store s = find(id, p);
    s.setName(req.name());
    s.setAddress(req.address());
    s.setPhone(req.phone());
    s.setIsActive(req.isActive());
    StoreResponse updated = map(storeRepository.save(s));
    cachedLookups.evictAllStores();
    return updated;
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.STORES_DELETE);
    storeRepository.delete(find(id, p));
    cachedLookups.evictAllStores();
  }

  private Store find(UUID id, UserPrincipal p) {
    return storeRepository
        .findById(id)
        .filter(s -> s.belongsTo(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", id));
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
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
