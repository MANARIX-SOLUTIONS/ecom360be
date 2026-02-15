package com.ecom360.platform.domain.repository;

import com.ecom360.platform.domain.model.PlatformConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, UUID> {
  Optional<PlatformConfig> findByKey(String key);
}
