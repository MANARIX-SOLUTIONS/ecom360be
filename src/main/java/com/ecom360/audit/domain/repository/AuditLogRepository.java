package com.ecom360.audit.domain.repository;
import com.ecom360.audit.domain.model.AuditLog;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByBusinessIdOrderByCreatedAtDesc(UUID bId, Pageable p);
    Page<AuditLog> findByBusinessIdAndEntityTypeOrderByCreatedAtDesc(UUID bId, String t, Pageable p);
    Page<AuditLog> findByBusinessIdAndUserIdOrderByCreatedAtDesc(UUID bId, UUID uId, Pageable p);
}
