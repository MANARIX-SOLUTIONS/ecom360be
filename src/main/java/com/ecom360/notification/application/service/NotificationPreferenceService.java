package com.ecom360.notification.application.service;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.notification.application.dto.NotificationPreferenceResponse;
import com.ecom360.notification.application.dto.NotificationPreferencesUpdateRequest;
import com.ecom360.notification.domain.model.NotificationPreference;
import com.ecom360.notification.domain.repository.NotificationPreferenceRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {
  private final NotificationPreferenceRepository repo;

  public NotificationPreferenceService(NotificationPreferenceRepository repo) {
    this.repo = repo;
  }

  public List<NotificationPreferenceResponse> getPreferences(UserPrincipal p) {
    List<NotificationPreference> existing = repo.findByUserIdOrderByType(p.userId());
    Map<String, Boolean> byType =
        existing.stream()
            .collect(
                Collectors.toMap(
                    NotificationPreference::getType, NotificationPreference::getEnabled));

    return NotificationTypes.ALL.stream()
        .sorted()
        .map(type -> new NotificationPreferenceResponse(type, byType.getOrDefault(type, true)))
        .toList();
  }

  @Transactional
  public List<NotificationPreferenceResponse> updatePreferences(
      UserPrincipal p, NotificationPreferencesUpdateRequest req) {
    if (req.preferences() == null || req.preferences().isEmpty()) {
      return getPreferences(p);
    }

    for (String type : NotificationTypes.ALL) {
      Boolean enabled = req.preferences().get(type);
      if (enabled == null) continue;

      NotificationPreference pref =
          repo.findByUserIdAndType(p.userId(), type)
              .orElseGet(
                  () -> {
                    NotificationPreference n = new NotificationPreference();
                    n.setUserId(p.userId());
                    n.setType(type);
                    return n;
                  });
      pref.setEnabled(enabled);
      repo.save(pref);
    }
    return getPreferences(p);
  }

  public boolean isEnabled(UUID userId, String type) {
    return repo.findByUserIdAndType(userId, type)
        .map(NotificationPreference::getEnabled)
        .orElse(true);
  }
}
