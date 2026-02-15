package com.ecom360.integration.domain.repository;
import com.ecom360.integration.domain.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.stereotype.Repository;
import java.util.List; import java.util.Optional; import java.util.UUID;
@Repository public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByBusinessId(UUID bId); Optional<ApiKey> findByKeyHash(String kh); Optional<ApiKey> findByBusinessIdAndId(UUID bId, UUID id);
}
