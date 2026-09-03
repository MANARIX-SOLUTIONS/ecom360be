package com.ecom360.sales.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ecom360.notification.application.service.NotificationPublisher;
import com.ecom360.notification.application.service.NotificationTypes;
import com.ecom360.sales.domain.model.Sale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaleDigitalPaymentNotificationTest {

  @Mock
  private NotificationPublisher notificationPublisher;

  private SaleService saleService;

  @BeforeEach
  void setUp() {
    saleService = new SaleService(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        notificationPublisher);
  }

  @Test
  void waveCompletedNotifiesOwners() {
    UUID businessId = UUID.randomUUID();
    UUID saleId = UUID.randomUUID();
    Sale sale = digitalSale(businessId, saleId, "wave", "completed");

    saleService.notifyDigitalPaymentReceived(sale);

    verify(notificationPublisher)
        .notifyOwnersAndManagers(
            eq(businessId),
            eq(NotificationTypes.PAYMENT_RECEIVED),
            eq("Paiement Wave reçu"),
            eq("2500 FCFA — reçu RCP-1."),
            eq("/sales?saleId=" + saleId));
  }

  @Test
  void cashCompletedDoesNotNotify() {
    Sale sale = digitalSale(UUID.randomUUID(), UUID.randomUUID(), "cash", "completed");

    saleService.notifyDigitalPaymentReceived(sale);

    verify(notificationPublisher, never())
        .notifyOwnersAndManagers(any(), any(), any(), any(), any());
  }

  @Test
  void pendingWaveDoesNotNotify() {
    Sale sale = digitalSale(UUID.randomUUID(), UUID.randomUUID(), "wave", "pending_payment");

    saleService.notifyDigitalPaymentReceived(sale);

    verify(notificationPublisher, never())
        .notifyOwnersAndManagers(any(), any(), any(), any(), any());
  }

  private static Sale digitalSale(
      UUID businessId, UUID saleId, String method, String status) {
    Sale sale = new Sale();
    sale.setId(saleId);
    sale.setBusinessId(businessId);
    sale.setPaymentMethod(method);
    sale.setStatus(status);
    sale.setTotal(2500);
    sale.setReceiptNumber("RCP-1");
    return sale;
  }
}
