package com.ecom360.admin.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.store.application.dto.StoreRequest;
import com.ecom360.store.application.dto.StoreResponse;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStoreService {

  private final BusinessRepository businessRepository;
  private final StoreRepository storeRepository;
  private final SubscriptionService subscriptionService;

  public AdminStoreService(
      BusinessRepository businessRepository,
      StoreRepository storeRepository,
      SubscriptionService subscriptionService) {
    this.businessRepository = businessRepository;
    this.storeRepository = storeRepository;
    this.subscriptionService = subscriptionService;
  }

  public List<StoreResponse> list(UUID businessId, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    return storeRepository.findByBusinessId(businessId).stream().map(this::map).toList();
  }

  @Transactional
  public StoreResponse create(UUID businessId, StoreRequest req, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    subscriptionService.assertCanAddStore(
        businessId, storeRepository.findByBusinessId(businessId).size());
    Store s = Store.create(businessId, req.name(), req.address(), req.phone());
    return map(storeRepository.save(s));
  }

  @Transactional
  public StoreResponse update(UUID businessId, UUID storeId, StoreRequest req, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    Store s = findStore(businessId, storeId);
    s.setName(req.name());
    s.setAddress(req.address());
    s.setPhone(req.phone());
    s.setIsActive(req.isActive());
    return map(storeRepository.save(s));
  }

  @Transactional
  public void delete(UUID businessId, UUID storeId, UserPrincipal p) {
    requirePlatformAdmin(p);
    businessRepository
        .findById(businessId)
        .orElseThrow(() -> new ResourceNotFoundException("Business", businessId));
    storeRepository.delete(findStore(businessId, storeId));
  }

  private Store findStore(UUID businessId, UUID storeId) {
    return storeRepository
        .findById(storeId)
        .filter(s -> s.belongsTo(businessId))
        .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));
  }

  private static void requirePlatformAdmin(UserPrincipal p) {
    if (p == null || !p.isPlatformAdmin()) {
      throw new AccessDeniedException("Platform admin only");
    }
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
