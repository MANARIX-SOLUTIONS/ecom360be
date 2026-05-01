package com.ecom360.admin.application.service;

import com.ecom360.admin.application.dto.AdminMaintenanceNotificationRequest;
import com.ecom360.admin.application.dto.AdminMaintenanceNotificationResult;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.notification.application.service.NotificationService;
import com.ecom360.notification.application.service.NotificationTypes;
import com.ecom360.shared.infrastructure.mail.EmailService;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMaintenanceNotificationService {

  private static final DateTimeFormatter FR_DT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withLocale(Locale.FRANCE);

  private final BusinessRepository businessRepository;
  private final BusinessUserRepository businessUserRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;
  private final EmailService emailService;

  public AdminMaintenanceNotificationService(
      BusinessRepository businessRepository,
      BusinessUserRepository businessUserRepository,
      UserRepository userRepository,
      NotificationService notificationService,
      EmailService emailService) {
    this.businessRepository = businessRepository;
    this.businessUserRepository = businessUserRepository;
    this.userRepository = userRepository;
    this.notificationService = notificationService;
    this.emailService = emailService;
  }

  @Transactional
  public AdminMaintenanceNotificationResult notifyMaintenance(
      AdminMaintenanceNotificationRequest req) {
    List<Business> businesses =
        req.businessIds() == null || req.businessIds().isEmpty()
            ? businessRepository.findAll()
            : businessRepository.findAllById(req.businessIds());

    int mailed = 0;
    int notified = 0;
    String actionUrl =
        req.statusPageUrl() != null && !req.statusPageUrl().isBlank()
            ? req.statusPageUrl().trim()
            : emailService.buildAppHomeLink();
    String windowText = buildWindow(req.startsAt(), req.endsAt());
    String fullBody = req.message() + (windowText.isBlank() ? "" : "\n\n" + windowText);

    for (Business b : businesses) {
      if (b.getEmail() != null && !b.getEmail().isBlank()) {
        emailService.sendMaintenanceNoticeEmail(
            b.getEmail(), b.getName(), req.title(), req.message(), windowText, actionUrl);
        mailed++;
      }

      for (BusinessUser bu : businessUserRepository.findByBusinessIdAndIsActive(b.getId(), true)) {
        User u = userRepository.findById(bu.getUserId()).orElse(null);
        if (u == null || !u.isActive()) {
          continue;
        }
        notificationService.createNotification(
            b.getId(), u.getId(), NotificationTypes.SYSTEM, req.title(), fullBody, actionUrl);
        notified++;
      }
    }

    return new AdminMaintenanceNotificationResult(businesses.size(), mailed, notified);
  }

  private static String buildWindow(OffsetDateTime startsAt, OffsetDateTime endsAt) {
    if (startsAt == null && endsAt == null) {
      return "";
    }
    ZoneId zone = ZoneId.of("Africa/Dakar");
    if (startsAt != null && endsAt != null) {
      return "Fenêtre de maintenance prévue du %s au %s (heure Dakar)."
          .formatted(
              FR_DT.format(startsAt.atZoneSameInstant(zone)),
              FR_DT.format(endsAt.atZoneSameInstant(zone)));
    }
    if (startsAt != null) {
      return "Début de maintenance prévu le %s (heure Dakar)."
          .formatted(FR_DT.format(startsAt.atZoneSameInstant(zone)));
    }
    return "Fin de maintenance prévue le %s (heure Dakar)."
        .formatted(FR_DT.format(endsAt.atZoneSameInstant(zone)));
  }
}
