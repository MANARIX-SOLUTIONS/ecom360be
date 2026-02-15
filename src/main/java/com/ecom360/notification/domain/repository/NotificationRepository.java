package com.ecom360.notification.domain.repository;

import com.ecom360.notification.domain.model.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID uId, Pageable p);

  Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(UUID uId, Boolean r, Pageable p);

  long countByUserIdAndIsRead(UUID uId, Boolean r);

  @Modifying
  @Query(
      "UPDATE Notification n SET n.isRead=true, n.readAt=CURRENT_TIMESTAMP WHERE n.userId=:u AND n.isRead=false")
  int markAllAsRead(@Param("u") UUID u);
}
