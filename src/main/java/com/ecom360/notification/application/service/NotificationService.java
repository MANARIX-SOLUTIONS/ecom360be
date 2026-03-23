package com.ecom360.notification.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.notification.application.dto.NotificationResponse;
import com.ecom360.notification.domain.model.Notification;
import com.ecom360.notification.domain.repository.NotificationRepository;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final NotificationRepository repo;
  private final NotificationPreferenceService preferenceService;

  public NotificationService(
      NotificationRepository repo, NotificationPreferenceService preferenceService) {
    this.repo = repo;
    this.preferenceService = preferenceService;
  }

  public Page<NotificationResponse> list(UserPrincipal p, Boolean unreadOnly, Pageable pg) {
    Page<Notification> page =
        Boolean.TRUE.equals(unreadOnly)
            ? repo.findByUserIdAndIsReadOrderByCreatedAtDesc(p.userId(), false, pg)
            : repo.findByUserIdOrderByCreatedAtDesc(p.userId(), pg);
    return page.map(this::map);
  }

  public long unreadCount(UserPrincipal p) {
    return repo.countByUserIdAndIsRead(p.userId(), false);
  }

  public NotificationResponse markAsRead(UUID id, UserPrincipal p) {
    Notification n =
        repo.findById(id)
            .filter(x -> x.getUserId().equals(p.userId()))
            .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    n.markRead();
    return map(repo.save(n));
  }

  @Transactional
  public int markAllAsRead(UserPrincipal p) {
    return repo.markAllAsRead(p.userId());
  }

  public void createNotification(
      UUID businessId, UUID userId, String type, String title, String body, String actionUrl) {
    if (!preferenceService.isEnabled(userId, type)) {
      return;
    }
    Notification n = new Notification();
    n.setBusinessId(businessId);
    n.setUserId(userId);
    n.setType(type);
    n.setTitle(title);
    n.setBody(body);
    n.setActionUrl(actionUrl);
    repo.save(n);
  }

  private NotificationResponse map(Notification n) {
    return new NotificationResponse(
        n.getId(),
        n.getBusinessId(),
        n.getUserId(),
        n.getType(),
        n.getTitle(),
        n.getBody(),
        n.getActionUrl(),
        n.getIsRead(),
        n.getReadAt(),
        n.getCreatedAt());
  }
}
