package com.ecom360.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.client.domain.model.Client;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.inventory.application.service.StockService;
import com.ecom360.sales.application.dto.SaleLineRequest;
import com.ecom360.sales.application.dto.SaleRequest;
import com.ecom360.sales.application.dto.SaleResponse;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SaleLine;
import com.ecom360.sales.domain.repository.SaleLineRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.InsufficientStockException;
import com.ecom360.store.domain.model.Store;
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
class SaleServiceTest {

  @Mock SaleRepository saleRepo;
  @Mock SaleLineRepository lineRepo;
  @Mock ProductRepository productRepo;
  @Mock StoreRepository storeRepo;
  @Mock ClientRepository clientRepo;
  @Mock StockService stockService;
  @Mock SubscriptionService subscriptionService;
  @Mock RolePermissionService permissionService;

  SaleService service;

  UUID businessId = UUID.randomUUID();
  UUID storeId = UUID.randomUUID();
  UUID userId = UUID.randomUUID();
  UUID clientId = UUID.randomUUID();
  UUID productId = UUID.randomUUID();
  UserPrincipal principal =
      new UserPrincipal(userId, "cashier@test.com", businessId, "caissier", null, false);

  @BeforeEach
  void setUp() {
    service =
        new SaleService(
            saleRepo,
            lineRepo,
            productRepo,
            storeRepo,
            clientRepo,
            stockService,
            subscriptionService,
            permissionService);
    when(subscriptionService.getPlanForBusiness(any())).thenReturn(Optional.empty());
  }

  @Test
  void replaySameClientSaleIdReturnsExistingWithoutSecondStockHit() {
    UUID clientSaleId = UUID.randomUUID();
    Sale existing = new Sale();
    existing.setId(UUID.randomUUID());
    existing.setBusinessId(businessId);
    existing.setStoreId(storeId);
    existing.setUserId(userId);
    existing.setClientId(clientId);
    existing.setPaymentMethod("cash");
    existing.setDiscountAmount(0);
    existing.setSubtotal(1000);
    existing.setTotal(1000);
    existing.setStatus("completed");
    existing.setReceiptNumber("RCP-1");
    existing.setClientSaleId(clientSaleId.toString());

    Store store = Store.create(businessId, "Boutique", "Dakar", null);
    when(storeRepo.findById(storeId)).thenReturn(Optional.of(store));
    Client client = namedClient();
    when(clientRepo.findByBusinessIdAndId(businessId, clientId)).thenReturn(Optional.of(client));
    when(saleRepo.findByBusinessIdAndClientSaleId(businessId, clientSaleId.toString()))
        .thenReturn(Optional.of(existing));
    when(lineRepo.findBySaleId(existing.getId())).thenReturn(List.of());
    when(storeRepo.findById(storeId)).thenReturn(Optional.of(store));

    SaleResponse first = service.createSale(request(clientSaleId), principal);
    SaleResponse second = service.createSale(request(clientSaleId), principal);

    assertThat(first.id()).isEqualTo(existing.getId());
    assertThat(second.id()).isEqualTo(existing.getId());
    verify(stockService, never()).updateStockForSale(any(), any(), any(), anyInt(), anyString());
  }

  @Test
  void insufficientStockBubblesAs409Exception() {
    UUID clientSaleId = UUID.randomUUID();
    Store store = Store.create(businessId, "Boutique", "Dakar", null);
    when(storeRepo.findById(storeId)).thenReturn(Optional.of(store));
    when(clientRepo.findByBusinessIdAndId(businessId, clientId))
        .thenReturn(Optional.of(namedClient()));
    when(saleRepo.findByBusinessIdAndClientSaleId(any(), any())).thenReturn(Optional.empty());
    Product product = product();
    when(productRepo.findByBusinessIdAndId(businessId, productId)).thenReturn(Optional.of(product));
    when(saleRepo.existsByReceiptNumber(any())).thenReturn(false);
    when(saleRepo.save(any(Sale.class)))
        .thenAnswer(
            inv -> {
              Sale s = inv.getArgument(0);
              if (s.getId() == null) s.setId(UUID.randomUUID());
              return s;
            });
    doThrow(InsufficientStockException.forSale(0, 1))
        .when(stockService)
        .updateStockForSale(any(), any(), any(), anyInt(), anyString());

    assertThatThrownBy(() -> service.createSale(request(clientSaleId), principal))
        .isInstanceOf(InsufficientStockException.class);
  }

  @Test
  void happyPathPersistsClientSaleId() {
    UUID clientSaleId = UUID.randomUUID();
    Store store = Store.create(businessId, "Boutique", "Dakar", null);
    when(storeRepo.findById(storeId)).thenReturn(Optional.of(store));
    when(clientRepo.findByBusinessIdAndId(businessId, clientId))
        .thenReturn(Optional.of(namedClient()));
    when(saleRepo.findByBusinessIdAndClientSaleId(any(), any())).thenReturn(Optional.empty());
    when(productRepo.findByBusinessIdAndId(businessId, productId))
        .thenReturn(Optional.of(product()));
    when(saleRepo.existsByReceiptNumber(any())).thenReturn(false);
    when(saleRepo.save(any(Sale.class)))
        .thenAnswer(
            inv -> {
              Sale s = inv.getArgument(0);
              if (s.getId() == null) s.setId(UUID.randomUUID());
              return s;
            });
    when(lineRepo.save(any(SaleLine.class))).thenAnswer(inv -> inv.getArgument(0));
    when(lineRepo.findBySaleId(any())).thenReturn(List.of());

    SaleResponse res = service.createSale(request(clientSaleId), principal);
    assertThat(res.id()).isNotNull();
    verify(saleRepo, org.mockito.Mockito.atLeastOnce())
        .save(
            org.mockito.ArgumentMatchers.argThat(
                s -> clientSaleId.toString().equals(s.getClientSaleId())));
  }

  private SaleRequest request(UUID clientSaleId) {
    return new SaleRequest(
        storeId,
        clientId,
        "cash",
        0,
        1000,
        null,
        List.of(new SaleLineRequest(productId, 1)),
        clientSaleId);
  }

  private Client namedClient() {
    Client c = new Client();
    c.setBusinessId(businessId);
    c.setName("Fatou Diallo");
    c.setCreditBalance(0);
    return c;
  }

  private Product product() {
    Product p = new Product();
    p.setId(productId);
    p.setBusinessId(businessId);
    p.setStoreId(storeId);
    p.setName("Savon");
    p.setSalePrice(1000);
    return p;
  }
}
