package com.ecom360.integration.commerce.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.commerce.application.dto.CommerceConnectionCreateRequest;
import com.ecom360.integration.commerce.application.dto.CommerceConnectionCreateResponse;
import com.ecom360.integration.commerce.application.dto.CommerceConnectionResponse;
import com.ecom360.integration.commerce.domain.model.CommerceConnection;
import com.ecom360.integration.commerce.domain.repository.CommerceConnectionRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommerceConnectionService {

  public static final String INCOMING_WEBHOOK_PATH_PREFIX =
      ApiConstants.API_BASE + "/public/commerce/webhooks/incoming/";

  private final CommerceConnectionRepository connectionRepository;
  private final StoreRepository storeRepository;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;

  public CommerceConnectionService(
      CommerceConnectionRepository connectionRepository,
      StoreRepository storeRepository,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService) {
    this.connectionRepository = connectionRepository;
    this.storeRepository = storeRepository;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
  }

  @Transactional
  public CommerceConnectionCreateResponse create(
      CommerceConnectionCreateRequest req, UserPrincipal p) {
    requireBiz(p);
    subscriptionService.requireFeatureApi(p.businessId());
    permissionService.require(p, Permission.COMMERCE_CONNECTIONS_CREATE);
    storeRepository
        .findById(req.storeId())
        .filter(s -> s.belongsTo(p.businessId()))
        .orElseThrow(() -> new ResourceNotFoundException("Store", req.storeId()));

    CommerceConnection c = new CommerceConnection();
    c.setBusinessId(p.businessId());
    c.setStoreId(req.storeId());
    c.setSourceType(req.sourceType().trim());
    c.setLabel(req.label().trim());
    c.setIncomingToken(generateIncomingToken());
    c.setHmacSecret(generateHmacSecret());
    c.setIsActive(true);
    c = connectionRepository.save(c);
    return toCreateResponse(c);
  }

  public List<CommerceConnectionResponse> list(UserPrincipal p) {
    requireBiz(p);
    subscriptionService.requireFeatureApi(p.businessId());
    permissionService.require(p, Permission.COMMERCE_CONNECTIONS_READ);
    return connectionRepository.findByBusinessIdOrderByLabelAsc(p.businessId()).stream()
        .map(this::toResponse)
        .toList();
  }

  public CommerceConnectionResponse getById(UUID id, UserPrincipal p) {
    requireBiz(p);
    subscriptionService.requireFeatureApi(p.businessId());
    permissionService.require(p, Permission.COMMERCE_CONNECTIONS_READ);
    return toResponse(findOwned(id, p));
  }

  @Transactional
  public void delete(UUID id, UserPrincipal p) {
    requireBiz(p);
    subscriptionService.requireFeatureApi(p.businessId());
    permissionService.require(p, Permission.COMMERCE_CONNECTIONS_DELETE);
    CommerceConnection c = findOwned(id, p);
    connectionRepository.delete(c);
  }

  private CommerceConnection findOwned(UUID id, UserPrincipal p) {
    return connectionRepository
        .findByBusinessIdAndId(p.businessId(), id)
        .orElseThrow(() -> new ResourceNotFoundException("CommerceConnection", id));
  }

  private CommerceConnectionResponse toResponse(CommerceConnection c) {
    return new CommerceConnectionResponse(
        c.getId(),
        c.getBusinessId(),
        c.getStoreId(),
        c.getSourceType(),
        c.getLabel(),
        INCOMING_WEBHOOK_PATH_PREFIX + c.getIncomingToken(),
        c.getIsActive(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }

  private CommerceConnectionCreateResponse toCreateResponse(CommerceConnection c) {
    return new CommerceConnectionCreateResponse(
        c.getId(),
        c.getBusinessId(),
        c.getStoreId(),
        c.getSourceType(),
        c.getLabel(),
        INCOMING_WEBHOOK_PATH_PREFIX + c.getIncomingToken(),
        c.getIsActive(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getHmacSecret());
  }

  private static String generateIncomingToken() {
    byte[] buf = new byte[32];
    new SecureRandom().nextBytes(buf);
    StringBuilder sb = new StringBuilder(64);
    for (byte b : buf) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static String generateHmacSecret() {
    return generateIncomingToken();
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
  }
}
