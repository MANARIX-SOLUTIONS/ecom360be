package com.ecom360.notification.application.service;

import com.ecom360.identity.application.service.CachedRolePermissions;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.notification.domain.repository.NotificationRepository;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes in-app notifications to business members whose role has at least one
 * of the given permissions and whose preference for {@code type} is on.
 */
@Service
public class NotificationPublisher {

  public static final Duration LOW_STOCK_DEDUP = Duration.ofHours(24);

  public static final Set<String> OWNER_MANAGER_ROLES = Set.of("PROPRIETAIRE", "GESTIONNAIRE");

  private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

  private final NotificationService notificationService;
  private final NotificationPreferenceService preferenceService;
  private final NotificationRepository notificationRepository;
  private final BusinessUserRepository businessUserRepository;
  private final CachedRolePermissions cachedRolePermissions;

  public NotificationPublisher(
      NotificationService notificationService,
      NotificationPreferenceService preferenceService,
      NotificationRepository notificationRepository,
      BusinessUserRepository businessUserRepository,
      CachedRolePermissions cachedRolePermissions) {
    this.notificationService = notificationService;
    this.preferenceService = preferenceService;
    this.notificationRepository = notificationRepository;
    this.businessUserRepository = businessUserRepository;
    this.cachedRolePermissions = cachedRolePermissions;
  }

  /**
   * @return user ids that received an in-app row (preference on)
   */
  public List<UUID> notifyBusiness(
      UUID businessId,
      String type,
      String title,
      String body,
      String actionUrl,
      Duration dedupWindow,
      Permission... requiredAny) {
    try {
      return notifyInternal(
          businessId,
          type,
          title,
          body,
          actionUrl,
          dedupWindow,
          null,
          requiredAny);
    } catch (Exception e) {
      log.warn(
          "Notification skipped business={} type={}: {}",
          businessId,
          type,
          e.getMessage());
      return List.of();
    }
  }

  public List<UUID> notifyBusiness(
      UUID businessId,
      String type,
      String title,
      String body,
      String actionUrl,
      Permission... requiredAny) {
    return notifyBusiness(
        businessId, type, title, body, actionUrl, null, requiredAny);
  }

  /** P / G only — used for POS Wave / OM so cashiers are not notified. */
  public List<UUID> notifyOwnersAndManagers(
      UUID businessId,
      String type,
      String title,
      String body,
      String actionUrl) {
    try {
      return notifyInternal(
          businessId,
          type,
          title,
          body,
          actionUrl,
          null,
          OWNER_MANAGER_ROLES);
    } catch (Exception e) {
      log.warn(
          "Notification skipped business={} type={}: {}",
          businessId,
          type,
          e.getMessage());
      return List.of();
    }
  }

  private List<UUID> notifyInternal(
      UUID businessId,
      String type,
      String title,
      String body,
      String actionUrl,
      Duration dedupWindow,
      Set<String> roleCodes,
      Permission... requiredAny) {
    if (businessId == null || type == null || type.isBlank()) {
      return List.of();
    }
    if (dedupWindow != null
        && actionUrl != null
        && notificationRepository.existsByBusinessIdAndTypeAndActionUrlAndCreatedAtAfter(
            businessId, type, actionUrl, Instant.now().minus(dedupWindow))) {
      return List.of();
    }

    List<UUID> notified = new ArrayList<>();
    for (BusinessUser bu : businessUserRepository.findByBusinessIdAndIsActive(businessId, true)) {
      if (!bu.isAccepted() || bu.getBusinessRole() == null) {
        continue;
      }
      if (roleCodes != null
          && !roleCodes.isEmpty()
          && !roleCodes.contains(bu.getBusinessRole().getCode())) {
        continue;
      }
      if (!hasAnyPermission(bu, requiredAny)) {
        continue;
      }
      UUID userId = bu.getUserId();
      if (!preferenceService.isEnabled(userId, type)) {
        continue;
      }
      notificationService.createNotification(
          businessId, userId, type, title, body, actionUrl);
      notified.add(userId);
    }
    return notified;
  }

  private boolean hasAnyPermission(BusinessUser bu, Permission... requiredAny) {
    if (requiredAny == null || requiredAny.length == 0) {
      return true;
    }
    Set<String> codes = cachedRolePermissions.codesForRole(bu.getBusinessRole().getId());
    for (Permission permission : requiredAny) {
      if (codes.contains(permission.name())) {
        return true;
      }
    }
    return false;
  }
}
