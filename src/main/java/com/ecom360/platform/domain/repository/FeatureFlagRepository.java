package com.ecom360.platform.domain.repository;

import com.ecom360.platform.domain.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    Optional<FeatureFlag> findByKey(String key);
    List<FeatureFlag> findAllByOrderByKeyAsc();
}
