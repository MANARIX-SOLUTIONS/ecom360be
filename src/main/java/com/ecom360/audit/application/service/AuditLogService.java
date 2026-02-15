package com.ecom360.audit.application.service;

import com.ecom360.audit.application.dto.AuditLogResponse;
import com.ecom360.audit.domain.model.AuditLog;
import com.ecom360.audit.domain.repository.AuditLogRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {
    private final AuditLogRepository repo;
    public AuditLogService(AuditLogRepository repo) { this.repo = repo; }

    public void log(UUID bizId, UUID userId, String action, String entityType, UUID entityId, Map<String, Object> changes, String ip) {
        repo.save(AuditLog.record(bizId, userId, action, entityType, entityId, changes, ip));
    }

    public Page<AuditLogResponse> list(UserPrincipal p, String entityType, UUID userId, Pageable pg) {
        if (!p.hasBusinessAccess()) throw new AccessDeniedException("Business context required");
        if (entityType != null) return repo.findByBusinessIdAndEntityTypeOrderByCreatedAtDesc(p.businessId(), entityType, pg).map(this::map);
        if (userId != null) return repo.findByBusinessIdAndUserIdOrderByCreatedAtDesc(p.businessId(), userId, pg).map(this::map);
        return repo.findByBusinessIdOrderByCreatedAtDesc(p.businessId(), pg).map(this::map);
    }

    private AuditLogResponse map(AuditLog a) { return new AuditLogResponse(a.getId(), a.getBusinessId(), a.getUserId(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getChanges(), a.getIpAddress(), a.getCreatedAt()); }
}
