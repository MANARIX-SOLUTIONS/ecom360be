package com.ecom360.tenant.application.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.notification.application.service.NotificationPublisher;
import com.ecom360.notification.application.service.NotificationTypes;
import com.ecom360.shared.infrastructure.mail.EmailService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckoutNotificationServiceTest {

    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    private SubscriptionCheckoutNotificationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionCheckoutNotificationService(
                notificationPublisher, userRepository, emailService);
        when(emailService.buildSubscriptionSettingsLink())
                .thenReturn("https://app.example/settings/subscription");
    }

    @Test
    void notifyPaidWritesInAppAndEmail() {
        UUID businessId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationPublisher.notifyBusiness(
                eq(businessId),
                eq(NotificationTypes.SUBSCRIPTION),
                eq("Abonnement activé — Pro"),
                eq("Le plan Pro (annuel) est actif jusqu’au 03/09/2027."),
                eq("/settings/subscription"),
                eq(Permission.SUBSCRIPTION_READ)))
                .thenReturn(List.of(userId));
        User user = User.create("Ada", "ada@example.com", "hash", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.notifyPaid(businessId, "Pro", "yearly", LocalDate.of(2027, 9, 3));

        verify(emailService)
                .sendSubscriptionCheckoutPaidEmail(
                        eq("ada@example.com"),
                        eq("Ada"),
                        eq("Pro"),
                        eq("annuel"),
                        eq("03/09/2027"),
                        eq("https://app.example/settings/subscription"));
    }

    @Test
    void notifyFailedWritesInAppAndEmail() {
        UUID businessId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationPublisher.notifyBusiness(
                eq(businessId),
                eq(NotificationTypes.SUBSCRIPTION),
                eq("Paiement d’abonnement échoué"),
                eq("Montant PayDunya incorrect."),
                eq("/settings/subscription"),
                eq(Permission.SUBSCRIPTION_READ)))
                .thenReturn(List.of(userId));
        User user = User.create("Ada", "ada@example.com", "hash", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.notifyFailed(businessId, "Montant PayDunya incorrect.");

        verify(emailService)
                .sendSubscriptionCheckoutFailedEmail(
                        eq("ada@example.com"),
                        eq("Ada"),
                        eq("Montant PayDunya incorrect."),
                        eq("https://app.example/settings/subscription"));
    }
}
