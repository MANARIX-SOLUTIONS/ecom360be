package com.ecom360.integration.domain.repository;
import com.ecom360.integration.domain.model.Webhook;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.List; import java.util.Optional; import java.util.UUID;
@Repository public interface WebhookRepository extends JpaRepository<Webhook, UUID> {
    List<Webhook> findByBusinessIdAndIsActive(UUID bId, Boolean a); List<Webhook> findByBusinessId(UUID bId); Optional<Webhook> findByBusinessIdAndId(UUID bId, UUID id);
}
