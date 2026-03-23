package com.ecom360.notification.domain.repository;

import com.ecom360.notification.domain.model.NotificationPreference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, UUID> {
  List<NotificationPreference> findByUserIdOrderByType(UUID userId);

  Optional<NotificationPreference> findByUserIdAndType(UUID userId, String type);
}
