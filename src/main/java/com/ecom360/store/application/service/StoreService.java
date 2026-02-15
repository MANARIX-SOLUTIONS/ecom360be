package com.ecom360.store.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
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

  public StoreService(StoreRepository storeRepository, SubscriptionService subscriptionService) {
    this.storeRepository = storeRepository;
    this.subscriptionService = subscriptionService;
  }

  public StoreResponse create(StoreRequest req, UserPrincipal p) {
    requireBiz(p);
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
    return map(find(id, p));
  }

  public List<StoreResponse> list(UserPrincipal p) {
    requireBiz(p);
    return storeRepository.findByBusinessIdAndIsActive(p.businessId(), true).stream()
        .map(this::map)
        .toList();
  }

  public StoreResponse update(UUID id, StoreRequest req, UserPrincipal p) {
    requireBiz(p);
    Store s = find(id, p);
    s.setName(req.name());
    s.setAddress(req.address());
    s.setPhone(req.phone());
    s.setIsActive(req.isActive());
    return map(storeRepository.save(s));
  }

  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
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
