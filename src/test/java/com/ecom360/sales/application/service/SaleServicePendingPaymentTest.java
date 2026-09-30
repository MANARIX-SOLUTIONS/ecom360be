package com.ecom360.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.notification.application.service.NotificationPublisher;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SaleLine;
import com.ecom360.sales.domain.model.SalePayment;
import com.ecom360.sales.domain.repository.SaleLineRepository;
import com.ecom360.sales.domain.repository.SalePaymentRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaleServicePendingPaymentTest {

  @Mock SaleRepository saleRepo;
  @Mock SaleLineRepository lineRepo;
  @Mock SalePaymentRepository salePaymentRepo;
  @Mock ProductRepository productRepo;
  @Mock StoreRepository storeRepo;
  @Mock ClientRepository clientRepo;
  @Mock StockService stockService;
  @Mock SubscriptionService subscriptionService;
  @Mock RolePermissionService permissionService;
  @Mock NotificationPublisher notificationPublisher;

  SaleService service;

  final UUID businessId = UUID.randomUUID();
  final UUID storeId = UUID.randomUUID();
  final UUID saleId = UUID.randomUUID();
  final UUID sellerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new SaleService(
        saleRepo,
        lineRepo,
        salePaymentRepo,
        productRepo,
        storeRepo,
        clientRepo,
        stockService,
        subscriptionService,
        permissionService,
        notificationPublisher);
  }

  @Test
  void complete_marksPaidAndRecordsOnePayment() {
    Sale sale = sale(Sale.STATUS_PENDING_PAYMENT);
    when(saleRepo.findByBusinessIdAndId(businessId, saleId)).thenReturn(Optional.of(sale));
    when(saleRepo.save(sale)).thenReturn(sale);

    service.completePendingPaymentSale(businessId, saleId, null, null);

    assertThat(sale.isCompleted()).isTrue();
    assertThat(sale.getAmountPaid()).isEqualTo(5000);
    verify(salePaymentRepo, times(1)).save(any(SalePayment.class));
    verify(notificationPublisher).notifyOwnersAndManagers(any(), any(), any(), any(), any());
  }

  @Test
  void complete_isIdempotent() {
    Sale sale = sale(Sale.STATUS_COMPLETED);
    when(saleRepo.findByBusinessIdAndId(businessId, saleId)).thenReturn(Optional.of(sale));

    service.completePendingPaymentSale(businessId, saleId, null, null);

    verify(saleRepo, never()).save(any());
    verifyNoInteractions(salePaymentRepo, notificationPublisher);
  }

  @Test
  void complete_refusesAFailedSale() {
    Sale sale = sale(Sale.STATUS_PAYMENT_FAILED);
    when(saleRepo.findByBusinessIdAndId(businessId, saleId)).thenReturn(Optional.of(sale));

    assertThatThrownBy(() -> service.completePendingPaymentSale(businessId, saleId, null, null))
        .isInstanceOf(BusinessRuleException.class);
    verifyNoInteractions(salePaymentRepo);
  }

  @Test
  void fail_restoresStockOfEveryLine() {
    Sale sale = sale(Sale.STATUS_PENDING_PAYMENT);
    UUID productA = UUID.randomUUID();
    UUID productB = UUID.randomUUID();
    when(saleRepo.findByBusinessIdAndId(businessId, saleId)).thenReturn(Optional.of(sale));
    when(lineRepo.findBySaleId(saleId)).thenReturn(List.of(line(productA, 2), line(productB, 3)));

    service.failPendingPaymentSale(businessId, saleId, null);

    assertThat(sale.getStatus()).isEqualTo(Sale.STATUS_PAYMENT_FAILED);
    verify(stockService).updateStockForPurchase(productA, storeId, sellerId, 2, "PAYFAIL-R-001");
    verify(stockService).updateStockForPurchase(productB, storeId, sellerId, 3, "PAYFAIL-R-001");
    verify(saleRepo).save(sale);
    verifyNoInteractions(salePaymentRepo);
  }

  @Test
  void fail_onCompletedSale_isNoOp() {
    Sale sale = sale(Sale.STATUS_COMPLETED);
    when(saleRepo.findByBusinessIdAndId(businessId, saleId)).thenReturn(Optional.of(sale));

    service.failPendingPaymentSale(businessId, saleId, null);

    assertThat(sale.isCompleted()).isTrue();
    verifyNoInteractions(stockService, lineRepo);
  }

  private Sale sale(String status) {
    Sale sale = new Sale();
    sale.setId(saleId);
    sale.setBusinessId(businessId);
    sale.setStoreId(storeId);
    sale.setUserId(sellerId);
    sale.setReceiptNumber("R-001");
    sale.setPaymentMethod("wave");
    sale.setSubtotal(5000);
    sale.setDiscountAmount(0);
    sale.setTotal(5000);
    sale.setAmountPaid(0);
    sale.setStatus(status);
    return sale;
  }

  private SaleLine line(UUID productId, int qty) {
    SaleLine line = new SaleLine();
    line.setSaleId(saleId);
    line.setProductId(productId);
    line.setQuantity(qty);
    line.setUnitPrice(1000);
    line.setLineTotal(qty * 1000);
    return line;
  }
}
