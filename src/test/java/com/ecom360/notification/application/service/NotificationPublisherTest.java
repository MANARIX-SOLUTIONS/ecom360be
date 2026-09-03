package com.ecom360.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.identity.application.service.CachedRolePermissions;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.notification.domain.repository.NotificationRepository;
import com.ecom360.tenant.domain.model.BusinessRole;
import com.ecom360.tenant.domain.model.BusinessUser;
import com.ecom360.tenant.domain.repository.BusinessUserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationPreferenceService preferenceService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private BusinessUserRepository businessUserRepository;
    @Mock
    private CachedRolePermissions cachedRolePermissions;

    private NotificationPublisher publisher;
    private UUID businessId;
    private UUID managerId;
    private UUID cashierId;
    private UUID managerRoleId;
    private UUID cashierRoleId;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(
                notificationService,
                preferenceService,
                notificationRepository,
                businessUserRepository,
                cachedRolePermissions);
        businessId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        cashierId = UUID.randomUUID();
        managerRoleId = UUID.randomUUID();
        cashierRoleId = UUID.randomUUID();
    }

    @Test
    void skipsMemberWithoutRequiredPermission() {
        stubMembers();
        when(cachedRolePermissions.codesForRole(managerRoleId))
                .thenReturn(Set.of(Permission.STOCK_READ.name()));
        when(cachedRolePermissions.codesForRole(cashierRoleId))
                .thenReturn(Set.of(Permission.SALES_CREATE.name()));
        when(preferenceService.isEnabled(managerId, NotificationTypes.LOW_STOCK))
                .thenReturn(true);

        List<UUID> notified = publisher.notifyBusiness(
                businessId,
                NotificationTypes.LOW_STOCK,
                "Stock bas",
                "Riz",
                "/products/1?storeId=s",
                Permission.STOCK_READ);

        assertThat(notified).containsExactly(managerId);
        verify(notificationService)
                .createNotification(
                        eq(businessId),
                        eq(managerId),
                        eq(NotificationTypes.LOW_STOCK),
                        eq("Stock bas"),
                        eq("Riz"),
                        eq("/products/1?storeId=s"));
        verify(notificationService, never())
                .createNotification(
                        eq(businessId),
                        eq(cashierId),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    void skipsWhenPreferenceOff() {
        stubMembers();
        when(cachedRolePermissions.codesForRole(managerRoleId))
                .thenReturn(Set.of(Permission.STOCK_READ.name()));
        when(cachedRolePermissions.codesForRole(cashierRoleId))
                .thenReturn(Set.of(Permission.STOCK_READ.name()));
        when(preferenceService.isEnabled(managerId, NotificationTypes.LOW_STOCK))
                .thenReturn(false);
        when(preferenceService.isEnabled(cashierId, NotificationTypes.LOW_STOCK))
                .thenReturn(true);

        List<UUID> notified = publisher.notifyBusiness(
                businessId,
                NotificationTypes.LOW_STOCK,
                "Stock bas",
                "Riz",
                "/products/1?storeId=s",
                Permission.STOCK_READ);

        assertThat(notified).containsExactly(cashierId);
        verify(notificationService, never())
                .createNotification(
                        eq(businessId), eq(managerId), any(), any(), any(), any());
    }

    @Test
    void notifyOwnersAndManagersSkipsCashier() {
        stubMembers();
        when(preferenceService.isEnabled(managerId, NotificationTypes.PAYMENT_RECEIVED))
                .thenReturn(true);

        List<UUID> notified = publisher.notifyOwnersAndManagers(
                businessId,
                NotificationTypes.PAYMENT_RECEIVED,
                "Paiement Wave reçu",
                "5000 FCFA",
                "/sales?saleId=1");

        assertThat(notified).containsExactly(managerId);
        verify(notificationService, never())
                .createNotification(
                        eq(businessId), eq(cashierId), any(), any(), any(), any());
        verify(cachedRolePermissions, never()).codesForRole(any());
    }

    @Test
    void skipsEntireBusinessWhenRecentDuplicateExists() {
        when(notificationRepository.existsByBusinessIdAndTypeAndActionUrlAndCreatedAtAfter(
                eq(businessId),
                eq(NotificationTypes.LOW_STOCK),
                eq("/products/1?storeId=s"),
                any()))
                .thenReturn(true);

        List<UUID> notified = publisher.notifyBusiness(
                businessId,
                NotificationTypes.LOW_STOCK,
                "Stock bas",
                "Riz",
                "/products/1?storeId=s",
                Duration.ofHours(24),
                Permission.STOCK_READ);

        assertThat(notified).isEmpty();
        verify(businessUserRepository, never()).findByBusinessIdAndIsActive(any(), any());
        verify(notificationService, never())
                .createNotification(any(), any(), any(), any(), any(), any());
    }

    private void stubMembers() {
        BusinessRole managerRole = BusinessRole.createSystem(businessId, "GESTIONNAIRE", "Gestionnaire", true);
        managerRole.setId(managerRoleId);
        BusinessRole cashierRole = BusinessRole.createSystem(businessId, "CAISSIER", "Caissier", true);
        cashierRole.setId(cashierRoleId);
        when(businessUserRepository.findByBusinessIdAndIsActive(businessId, true))
                .thenReturn(
                        List.of(
                                BusinessUser.create(businessId, managerId, managerRole),
                                BusinessUser.create(businessId, cashierId, cashierRole)));
    }
}
