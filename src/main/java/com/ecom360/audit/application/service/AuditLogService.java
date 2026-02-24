package com.ecom360.audit.application.service;

import com.ecom360.audit.application.dto.AuditLogResponse;
import com.ecom360.audit.domain.model.AuditLog;
import com.ecom360.audit.domain.repository.AuditLogRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.infrastructure.web.RequestContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
  private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository repo;

  public AuditLogService(AuditLogRepository repo) {
    this.repo = repo;
  }

  /**
   * Records an audit event. Uses IP and requestId from RequestContext when null. Runs synchronously.
   */
  public void log(
      UUID bizId,
      UUID userId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> changes,
      String ip) {
    String effectiveIp = ip != null ? ip : RequestContext.getClientIp();
    String requestId = RequestContext.getRequestId();
    repo.save(
        AuditLog.record(bizId, userId, action, entityType, entityId, changes, effectiveIp, requestId));
  }

  /**
   * Records an audit event asynchronously. Context (ip, requestId) must be passed explicitly since
   * ThreadLocal does not propagate to async threads.
   */
  @Async
  public void logAsync(
      UUID bizId,
      UUID userId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> changes,
      String ip,
      String requestId) {
    try {
      repo.save(
          AuditLog.record(bizId, userId, action, entityType, entityId, changes, ip, requestId));
    } catch (Exception e) {
      log.warn("Failed to persist audit log: {} {} {} - {}", action, entityType, entityId, e.getMessage());
    }
  }

  /**
   * Convenience: log asynchronously using current RequestContext. Call from request thread only.
   */
  public void logAsync(
      UUID bizId,
      UUID userId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> changes) {
    logAsync(
        bizId,
        userId,
        action,
        entityType,
        entityId,
        changes,
        RequestContext.getClientIp(),
        RequestContext.getRequestId());
  }

  public Page<AuditLogResponse> list(UserPrincipal p, String entityType, UUID userId, Pageable pg) {
    if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
    if (entityType != null)
      return repo.findByBusinessIdAndEntityTypeOrderByCreatedAtDesc(p.businessId(), entityType, pg)
          .map(this::map);
    if (userId != null)
      return repo.findByBusinessIdAndUserIdOrderByCreatedAtDesc(p.businessId(), userId, pg)
          .map(this::map);
    return repo.findByBusinessIdOrderByCreatedAtDesc(p.businessId(), pg).map(this::map);
  }

  /**
   * Platform admin: list audit logs across all businesses, optionally filtered by businessId,
   * entityType, or userId.
   */
  public Page<AuditLogResponse> listForAdmin(
      UserPrincipal p,
      UUID businessId,
      String entityType,
      UUID userId,
      Pageable pg) {
    if (!p.isPlatformAdmin()) throw new AccessDeniedException("Platform admin required");
    if (businessId != null) {
      if (entityType != null)
        return repo.findByBusinessIdAndEntityTypeOrderByCreatedAtDesc(businessId, entityType, pg)
            .map(this::map);
      if (userId != null)
        return repo.findByBusinessIdAndUserIdOrderByCreatedAtDesc(businessId, userId, pg)
            .map(this::map);
      return repo.findByBusinessIdOrderByCreatedAtDesc(businessId, pg).map(this::map);
    }
    return repo.findAllByOrderByCreatedAtDesc(pg).map(this::map);
  }

  private AuditLogResponse map(AuditLog a) {
    return new AuditLogResponse(
        a.getId(),
        a.getBusinessId(),
        a.getUserId(),
        a.getAction(),
        a.getEntityType(),
        a.getEntityId(),
        a.getChanges(),
        a.getIpAddress(),
        a.getRequestId(),
        a.getCreatedAt());
  }
}
