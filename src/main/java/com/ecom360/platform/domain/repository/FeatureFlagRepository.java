package com.ecom360.platform.domain.repository;

import com.ecom360.platform.domain.model.FeatureFlag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
  Optional<FeatureFlag> findByKey(String key);

  List<FeatureFlag> findAllByOrderByKeyAsc();
}
