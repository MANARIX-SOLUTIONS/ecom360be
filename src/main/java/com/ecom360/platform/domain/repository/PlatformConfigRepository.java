package com.ecom360.platform.domain.repository;

import com.ecom360.platform.domain.model.PlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, UUID> {
    Optional<PlatformConfig> findByKey(String key);
}
