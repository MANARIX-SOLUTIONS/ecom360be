package com.ecom360.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecom360.catalog.application.dto.PerformerSnapshot;
import com.ecom360.catalog.application.service.ProductPerformerService;
import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.model.Client;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.notification.application.service.NotificationPublisher;
import com.ecom360.sales.application.dto.ImportedSaleLine;
import com.ecom360.sales.application.dto.SaleLineRequest;
import com.ecom360.sales.application.dto.SaleRequest;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SaleLine;
import com.ecom360.sales.domain.repository.SaleLineRepository;
import com.ecom360.sales.domain.repository.SalePaymentRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.store.domain.model.Store;
import com.ecom360.store.domain.repository.StoreRepository;
import com.ecom360.tenant.application.service.SubscriptionService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaleServicePerformerTest {

  @Mock private SaleRepository saleRepo;
  @Mock private SaleLineRepository lineRepo;
  @Mock private SalePaymentRepository salePaymentRepo;
  @Mock private ProductRepository productRepo;
  @Mock private StoreRepository storeRepo;
  @Mock private ClientRepository clientRepo;
  @Mock private StockService stockService;
  @Mock private SubscriptionService subscriptionService;
  @Mock private RolePermissionService permissionService;
  @Mock private NotificationPublisher notificationPublisher;
  @Mock private ProductPerformerService productPerformerService;

  private SaleService saleService;
  private UUID businessId;
  private UUID storeId;
  private UserPrincipal principal;
  private Product braids;

  @BeforeEach
  void setUp() {
    saleService =
        new SaleService(
            saleRepo,
            lineRepo,
            salePaymentRepo,
            productRepo,
            storeRepo,
            clientRepo,
            stockService,
            subscriptionService,
            permissionService,
            notificationPublisher,
            productPerformerService);
    businessId = UUID.randomUUID();
    storeId = UUID.randomUUID();
    principal =
        new UserPrincipal(UUID.randomUUID(), "cashier@test.sn", businessId, "OWNER", null, false);
    braids = new Product();
    braids.setId(UUID.randomUUID());
    braids.setBusinessId(businessId);
    braids.setStoreId(storeId);
    braids.setName("Tresses");
    braids.setUnit("prestation");
    braids.setSalePrice(15000);

    when(storeRepo.findById(storeId))
        .thenReturn(Optional.of(Store.create(businessId, "Salon", null, null)));
    when(productRepo.findByBusinessIdAndId(businessId, braids.getId()))
        .thenReturn(Optional.of(braids));
    when(saleRepo.save(any(Sale.class)))
        .thenAnswer(
            inv -> {
              Sale sale = inv.getArgument(0);
              if (sale.getId() == null) {
                sale.setId(UUID.randomUUID());
              }
              return sale;
            });
  }

  @Test
  void posSaleKeepsOnePerformerPerLine() {
    UUID awa = UUID.randomUUID();
    UUID fatou = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    when(clientRepo.findByBusinessIdAndId(businessId, clientId))
        .thenReturn(Optional.of(mock(Client.class)));
    when(productPerformerService.resolveForPosSale(businessId, storeId, braids, awa))
        .thenReturn(new PerformerSnapshot(awa, "Awa Diop"));
    when(productPerformerService.resolveForPosSale(businessId, storeId, braids, fatou))
        .thenReturn(new PerformerSnapshot(fatou, "Fatou Sow"));

    saleService.createSale(
        new SaleRequest(
            storeId,
            clientId,
            "cash",
            0,
            null,
            null,
            null,
            null,
            List.of(
                new SaleLineRequest(braids.getId(), 2, awa),
                new SaleLineRequest(braids.getId(), 1, fatou))),
        principal);

    ArgumentCaptor<SaleLine> lines = ArgumentCaptor.forClass(SaleLine.class);
    verify(lineRepo, times(2)).save(lines.capture());
    assertThat(lines.getAllValues())
        .extracting(
            SaleLine::getQuantity, SaleLine::getPerformerBusinessUserId, SaleLine::getPerformerName)
        .containsExactly(tuple(2, awa, "Awa Diop"), tuple(1, fatou, "Fatou Sow"));
  }

  @Test
  void commerceImportLeavesPerformerEmpty() {
    saleService.createSaleFromImport(
        businessId,
        storeId,
        principal.userId(),
        "cash",
        0,
        null,
        List.of(new ImportedSaleLine(braids.getId(), null, 1, 15000)));

    ArgumentCaptor<SaleLine> line = ArgumentCaptor.forClass(SaleLine.class);
    verify(lineRepo).save(line.capture());
    assertThat(line.getValue().getPerformerBusinessUserId()).isNull();
    assertThat(line.getValue().getPerformerName()).isNull();
    verifyNoInteractions(productPerformerService);
    verify(stockService).updateStockForSale(eq(braids.getId()), eq(storeId), any(), eq(1), any());
  }
}
