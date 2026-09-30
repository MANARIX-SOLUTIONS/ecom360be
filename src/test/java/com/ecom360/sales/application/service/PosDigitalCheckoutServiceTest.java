package com.ecom360.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.client.domain.repository.ClientRepository;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.sales.application.dto.DigitalCheckoutAvailabilityResponse;
import com.ecom360.sales.application.dto.DigitalCheckoutRequest;
import com.ecom360.sales.application.dto.DigitalCheckoutResponse;
import com.ecom360.sales.application.dto.SaleLineRequest;
import com.ecom360.sales.domain.model.Sale;
import com.ecom360.sales.domain.model.SalePaymentIntent;
import com.ecom360.sales.domain.model.SalePaymentIntentStatus;
import com.ecom360.sales.domain.repository.SalePaymentIntentRepository;
import com.ecom360.sales.domain.repository.SaleRepository;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.payment.application.service.BusinessPaymentProviderService;
import com.ecom360.tenant.payment.domain.PaymentIntentNotFoundException;
import com.ecom360.tenant.payment.domain.model.BusinessPaymentProvider;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysChargeResult;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysCredentials;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysProperties;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysStatusResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PosDigitalCheckoutServiceTest {

  private static final int AMOUNT = 5000;
  private static final String TX_ID = "tx_1";

  @Mock SalePaymentIntentRepository intentRepository;
  @Mock SaleRepository saleRepository;
  @Mock SaleService saleService;
  @Mock ClientRepository clientRepository;
  @Mock BusinessPaymentProviderService providerService;
  @Mock BictorysClient bictorysClient;
  @Mock SubscriptionService subscriptionService;
  @Mock RolePermissionService permissionService;
  @Mock AuditLogService auditLogService;
  @Mock EntityManager entityManager;
  @Mock PlatformTransactionManager transactionManager;

  final ObjectMapper mapper = new ObjectMapper();
  PosDigitalCheckoutService service;

  final UUID businessId = UUID.randomUUID();
  final UUID userId = UUID.randomUUID();
  final UUID storeId = UUID.randomUUID();
  final UUID clientId = UUID.randomUUID();
  final UUID saleId = UUID.randomUUID();
  final UUID intentId = UUID.randomUUID();
  final BictorysCredentials businessCreds =
      BictorysCredentials.forEnvironment(false, "biz_key", "biz_secret", "SN");
  UserPrincipal cashier;

  @BeforeEach
  void setUp() {
    service = new PosDigitalCheckoutService(
        intentRepository,
        saleRepository,
        saleService,
        clientRepository,
        providerService,
        bictorysClient,
        new BictorysProperties(),
        subscriptionService,
        permissionService,
        auditLogService,
        mapper,
        entityManager,
        transactionManager,
        "https://app.example.com");
    cashier = new UserPrincipal(userId, "cashier@example.com", businessId, "caissier", null, false);
    lenient().when(intentRepository.save(any(SalePaymentIntent.class))).thenAnswer(inv -> {
      SalePaymentIntent i = inv.getArgument(0);
      if (i.getId() == null) {
        ReflectionTestUtils.setField(i, "id", intentId);
      }
      return i;
    });
  }

  // --- start -----------------------------------------------------------------

  @Test
  void start_createsPendingSaleAndChargesTheBusinessAccount() {
    when(providerService.requireUsableCredentials(businessId)).thenReturn(businessCreds);
    when(saleService.createPendingPaymentSale(
            eq(storeId), eq(clientId), eq("wave"), eq(0), isNull(), any(), eq(cashier)))
        .thenReturn(pendingSale());
    when(bictorysClient.createCharge(
            eq(businessCreds),
            eq(AMOUNT),
            eq("wave"),
            eq(intentId.toString()),
            eq("https://app.example.com/pos?checkout=" + intentId),
            anyString(),
            isNull(),
            isNull(),
            isNull()))
        .thenReturn(new BictorysChargeResult(TX_ID, null, "https://pay.link/x", "iVBORqr", "Composez #144#"));

    DigitalCheckoutResponse res = service.start(request("wave"), cashier);

    assertThat(res.status()).isEqualTo(SalePaymentIntentStatus.PENDING);
    assertThat(res.amount()).isEqualTo(AMOUNT);
    assertThat(res.saleId()).isEqualTo(saleId);
    assertThat(res.qrCode()).isEqualTo("iVBORqr");
    assertThat(res.paymentLink()).isEqualTo("https://pay.link/x");
    assertThat(res.ussdMessage()).isEqualTo("Composez #144#");
    assertThat(res.sale()).isNull();
    verify(saleService, never()).completePendingPaymentSale(any(), any(), any(), any());
  }

  @Test
  void start_outsideBusinessPlan_isRejectedBeforeAnySale() {
    doThrow(new BusinessRuleException("Paiement Wave / Orange Money en ligne réservé au plan Business"))
        .when(subscriptionService).requirePosOnlinePayment(businessId);

    assertThatThrownBy(() -> service.start(request("wave"), cashier))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("plan Business");
    verifyNoInteractions(saleService, bictorysClient, providerService);
  }

  @Test
  void start_withoutBictorysKeys_givesActionableError() {
    when(providerService.requireUsableCredentials(businessId))
        .thenThrow(new BusinessRuleException(BusinessPaymentProviderService.NOT_CONFIGURED));

    assertThatThrownBy(() -> service.start(request("orange_money"), cashier))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Paiements Bictorys");
    verifyNoInteractions(saleService, bictorysClient);
  }

  @Test
  void start_zeroTotal_isRejected() {
    when(providerService.requireUsableCredentials(businessId)).thenReturn(businessCreds);
    Sale free = pendingSale();
    free.setTotal(0);
    when(saleService.createPendingPaymentSale(any(), any(), any(), eq(0), any(), any(), any()))
        .thenReturn(free);

    assertThatThrownBy(() -> service.start(request("wave"), cashier))
        .isInstanceOf(BusinessRuleException.class);
    verifyNoInteractions(bictorysClient);
  }

  // --- webhook ---------------------------------------------------------------

  @Test
  void webhook_success_completesSaleOnce_evenWhenRedelivered() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));

    service.handleWebhook(account(businessId), payload("succeeded", AMOUNT));
    service.handleWebhook(account(businessId), payload("succeeded", AMOUNT));

    assertThat(intent.getStatus()).isEqualTo(SalePaymentIntentStatus.PAID);
    verify(saleService, times(1)).completePendingPaymentSale(businessId, saleId, null, null);
    verify(saleService, never()).failPendingPaymentSale(any(), any(), any());
  }

  @Test
  void webhook_afterDowngrade_stillConfirms() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));

    service.handleWebhook(account(businessId), payload("succeeded", AMOUNT));

    assertThat(intent.isSettled()).isTrue();
    verifyNoInteractions(subscriptionService);
  }

  @Test
  void webhook_wrongAmount_failsAndReleasesStock() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));

    service.handleWebhook(account(businessId), payload("succeeded", AMOUNT - 1000));

    assertThat(intent.getStatus()).isEqualTo(SalePaymentIntentStatus.FAILED);
    assertThat(intent.getFailureReason()).contains("Montant");
    verify(saleService).failPendingPaymentSale(businessId, saleId, null);
    verify(saleService, never()).completePendingPaymentSale(any(), any(), any(), any());
  }

  @Test
  void webhook_wrongCurrency_fails() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    ObjectNode body = payload("succeeded", AMOUNT);
    body.put("currency", "EUR");

    service.handleWebhook(account(businessId), body);

    assertThat(intent.getStatus()).isEqualTo(SalePaymentIntentStatus.FAILED);
    verify(saleService).failPendingPaymentSale(businessId, saleId, null);
  }

  @Test
  void webhook_paymentFailed_releasesStock() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));

    service.handleWebhook(account(businessId), payload("failed", AMOUNT));

    assertThat(intent.getStatus()).isEqualTo(SalePaymentIntentStatus.FAILED);
    verify(saleService).failPendingPaymentSale(businessId, saleId, null);
  }

  @Test
  void webhook_onAnotherBusinessUrl_isIgnored() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));

    service.handleWebhook(account(UUID.randomUUID()), payload("succeeded", AMOUNT));

    assertThat(intent.isPending()).isTrue();
    verifyNoInteractions(saleService);
  }

  @Test
  void webhook_lateSuccessAfterExpiry_doesNotReopenTheSale() {
    SalePaymentIntent intent = pendingIntent();
    intent.markClosed(SalePaymentIntentStatus.EXPIRED, "expired");
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));

    service.handleWebhook(account(businessId), payload("succeeded", AMOUNT));

    assertThat(intent.getStatus()).isEqualTo(SalePaymentIntentStatus.EXPIRED);
    verifyNoInteractions(saleService);
    verify(auditLogService).log(
        eq(businessId), isNull(), eq("sale.digital_checkout.late_success"), any(), any(), any(), any());
  }

  @Test
  void webhook_unknownIntent_asksForRetry() {
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.empty());
    when(intentRepository.findByExternalTokenForUpdate(TX_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.handleWebhook(account(businessId), payload("succeeded", AMOUNT)))
        .isInstanceOf(PaymentIntentNotFoundException.class);
  }

  @Test
  void webhook_matchesByTransactionIdWhenReferenceMissing() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findByExternalTokenForUpdate(TX_ID)).thenReturn(Optional.of(intent));
    ObjectNode body = payload("succeeded", AMOUNT);
    body.remove("paymentReference");

    service.handleWebhook(account(businessId), body);

    assertThat(intent.isSettled()).isTrue();
  }

  // --- status / cancel / manual / expiry --------------------------------------

  @Test
  void getStatus_syncsWithBictorysWithoutPlanCheck() {
    stubOwnedIntent(pendingIntent());
    when(providerService.findCredentials(businessId)).thenReturn(Optional.of(businessCreds));
    when(bictorysClient.getStatus(businessCreds, TX_ID))
        .thenReturn(new BictorysStatusResult("succeeded", AMOUNT));

    DigitalCheckoutResponse res = service.getStatus(intentId, cashier);

    assertThat(res.status()).isEqualTo(SalePaymentIntentStatus.PAID);
    verify(saleService).completePendingPaymentSale(businessId, saleId, null, null);
    verifyNoInteractions(subscriptionService);
  }

  @Test
  void getStatus_ofAnotherBusiness_isDenied() {
    when(intentRepository.findBusinessIdById(intentId)).thenReturn(Optional.of(UUID.randomUUID()));

    assertThatThrownBy(() -> service.getStatus(intentId, cashier))
        .isInstanceOf(AccessDeniedException.class);
    verify(intentRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void cancel_whenCustomerAlreadyPaid_confirmsInstead() {
    stubOwnedIntent(pendingIntent());
    when(providerService.findCredentials(businessId)).thenReturn(Optional.of(businessCreds));
    when(bictorysClient.getStatus(businessCreds, TX_ID))
        .thenReturn(new BictorysStatusResult("succeeded", AMOUNT));

    DigitalCheckoutResponse res = service.cancel(intentId, cashier);

    assertThat(res.status()).isEqualTo(SalePaymentIntentStatus.PAID);
    verify(saleService, never()).failPendingPaymentSale(any(), any(), any());
  }

  @Test
  void cancel_pending_releasesStock() {
    SalePaymentIntent intent = pendingIntent();
    stubOwnedIntent(intent);
    when(providerService.findCredentials(businessId)).thenReturn(Optional.of(businessCreds));
    when(bictorysClient.getStatus(businessCreds, TX_ID))
        .thenReturn(new BictorysStatusResult("pending", null));

    DigitalCheckoutResponse res = service.cancel(intentId, cashier);

    assertThat(res.status()).isEqualTo(SalePaymentIntentStatus.CANCELLED);
    verify(saleService).failPendingPaymentSale(businessId, saleId, userId);
  }

  @Test
  void confirmManual_completesSaleWithActor() {
    SalePaymentIntent intent = pendingIntent();
    stubOwnedIntent(intent);

    DigitalCheckoutResponse res = service.confirmManual(intentId, cashier);

    assertThat(res.status()).isEqualTo(SalePaymentIntentStatus.MANUAL);
    verify(saleService).completePendingPaymentSale(eq(businessId), eq(saleId), eq(userId), anyString());
  }

  @Test
  void confirmManual_onClosedIntent_isRejected() {
    SalePaymentIntent intent = pendingIntent();
    intent.markClosed(SalePaymentIntentStatus.CANCELLED, "x");
    stubOwnedIntent(intent);

    assertThatThrownBy(() -> service.confirmManual(intentId, cashier))
        .isInstanceOf(BusinessRuleException.class);
    verifyNoInteractions(saleService);
  }

  @Test
  void expireStalePendingIntents_releasesStockWhenStillUnpaid() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findIdsByStatusAndExpiresAtBefore(eq(SalePaymentIntentStatus.PENDING), any()))
        .thenReturn(List.of(intentId));
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    when(providerService.findCredentials(businessId)).thenReturn(Optional.of(businessCreds));
    when(bictorysClient.getStatus(businessCreds, TX_ID))
        .thenReturn(new BictorysStatusResult("pending", null));

    int expired = service.expireStalePendingIntents();

    assertThat(expired).isEqualTo(1);
    assertThat(intent.getStatus()).isEqualTo(SalePaymentIntentStatus.EXPIRED);
    verify(saleService).failPendingPaymentSale(businessId, saleId, null);
  }

  @Test
  void expireStalePendingIntents_confirmsPaymentsThatArrivedMeanwhile() {
    SalePaymentIntent intent = pendingIntent();
    when(intentRepository.findIdsByStatusAndExpiresAtBefore(eq(SalePaymentIntentStatus.PENDING), any()))
        .thenReturn(List.of(intentId));
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    when(providerService.findCredentials(businessId)).thenReturn(Optional.of(businessCreds));
    when(bictorysClient.getStatus(businessCreds, TX_ID))
        .thenReturn(new BictorysStatusResult("succeeded", AMOUNT));

    assertThat(service.expireStalePendingIntents()).isZero();
    assertThat(intent.getStatus()).isEqualTo(SalePaymentIntentStatus.PAID);
    verify(saleService, never()).failPendingPaymentSale(any(), any(), any());
  }

  @Test
  void availability_requiresBusinessPlanAndKeys() {
    when(subscriptionService.hasPosOnlinePayment(businessId)).thenReturn(false);
    DigitalCheckoutAvailabilityResponse pro = service.availability(cashier);
    assertThat(pro.planAllowed()).isFalse();
    assertThat(pro.available()).isFalse();
    verify(providerService, never()).isConfigured(any());

    when(subscriptionService.hasPosOnlinePayment(businessId)).thenReturn(true);
    when(providerService.isConfigured(businessId)).thenReturn(true);
    assertThat(service.availability(cashier).available()).isTrue();
  }

  // --- helpers ---------------------------------------------------------------

  private DigitalCheckoutRequest request(String channel) {
    return new DigitalCheckoutRequest(
        storeId, clientId, channel, 0, null, List.of(new SaleLineRequest(UUID.randomUUID(), 2)));
  }

  private Sale pendingSale() {
    Sale sale = new Sale();
    sale.setId(saleId);
    sale.setBusinessId(businessId);
    sale.setStoreId(storeId);
    sale.setClientId(clientId);
    sale.setUserId(userId);
    sale.setTotal(AMOUNT);
    sale.setStatus(Sale.STATUS_PENDING_PAYMENT);
    return sale;
  }

  private SalePaymentIntent pendingIntent() {
    SalePaymentIntent intent = new SalePaymentIntent();
    ReflectionTestUtils.setField(intent, "id", intentId);
    intent.setBusinessId(businessId);
    intent.setStoreId(storeId);
    intent.setSaleId(saleId);
    intent.setUserId(userId);
    intent.setAmount(AMOUNT);
    intent.setChannel("wave");
    intent.setExternalToken(TX_ID);
    intent.setExpiresAt(Instant.now().plusSeconds(900));
    return intent;
  }

  private void stubOwnedIntent(SalePaymentIntent intent) {
    when(intentRepository.findBusinessIdById(intentId)).thenReturn(Optional.of(businessId));
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
  }

  private static BusinessPaymentProvider account(UUID owner) {
    BusinessPaymentProvider account = new BusinessPaymentProvider();
    account.setBusinessId(owner);
    return account;
  }

  private ObjectNode payload(String status, int amount) {
    ObjectNode node = mapper.createObjectNode();
    node.put("id", TX_ID);
    node.put("type", "payment");
    node.put("status", status);
    node.put("amount", amount);
    node.put("currency", "XOF");
    node.put("paymentReference", intentId.toString());
    return node;
  }
}
