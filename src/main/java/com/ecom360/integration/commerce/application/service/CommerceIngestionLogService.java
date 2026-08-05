package com.ecom360.integration.commerce.application.service;

import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.integration.commerce.application.dto.CommerceIngestionLogResponse;
import com.ecom360.integration.commerce.domain.model.CommerceOrderIngestionLog;
import com.ecom360.integration.commerce.domain.repository.CommerceConnectionRepository;
import com.ecom360.integration.commerce.domain.repository.CommerceOrderIngestionLogRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CommerceIngestionLogService {

  private final CommerceOrderIngestionLogRepository logRepository;
  private final CommerceConnectionRepository connectionRepository;
  private final RolePermissionService permissionService;
  private final SubscriptionService subscriptionService;

  public CommerceIngestionLogService(
      CommerceOrderIngestionLogRepository logRepository,
      CommerceConnectionRepository connectionRepository,
      RolePermissionService permissionService,
      SubscriptionService subscriptionService) {
    this.logRepository = logRepository;
    this.connectionRepository = connectionRepository;
    this.permissionService = permissionService;
    this.subscriptionService = subscriptionService;
  }

  public Page<CommerceIngestionLogResponse> list(
      UserPrincipal p, UUID connectionId, Pageable pageable) {
    requireBiz(p);
    subscriptionService.requireFeatureApi(p.businessId());
    permissionService.require(p, Permission.COMMERCE_CONNECTIONS_READ);

    Page<CommerceOrderIngestionLog> page;
    if (connectionId != null) {
      connectionRepository
          .findByBusinessIdAndId(p.businessId(), connectionId)
          .orElseThrow(() -> new ResourceNotFoundException("CommerceConnection", connectionId));
      page = logRepository.findByBusinessIdAndConnectionIdOrderByCreatedAtDesc(
          p.businessId(), connectionId, pageable);
    } else {
      page = logRepository.findByBusinessIdOrderByCreatedAtDesc(p.businessId(), pageable);
    }
    return page.map(this::toResponse);
  }

  private CommerceIngestionLogResponse toResponse(CommerceOrderIngestionLog l) {
    return new CommerceIngestionLogResponse(
        l.getId(),
        l.getConnectionId(),
        l.getBusinessId(),
        l.getSourceType(),
        l.getExternalOrderId(),
        l.getStatus(),
        l.getErrorMessage(),
        l.getSaleId(),
        l.getCreatedAt());
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess())
      throw new AccessDeniedException("Business context required");
  }
}
