package com.ecom360.tenant.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.domain.model.Invoice;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.model.SubscriptionStatus;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.InvoiceRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.payment.domain.PaymentIntentNotFoundException;
import com.ecom360.tenant.payment.domain.model.SubscriptionPaymentIntent;
import com.ecom360.tenant.payment.domain.model.SubscriptionPaymentStatus;
import com.ecom360.tenant.payment.domain.repository.SubscriptionPaymentIntentRepository;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysProperties;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysStatusResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckoutServiceTest {

  @Mock
  SubscriptionPaymentIntentRepository intentRepository;
  @Mock
  SubscriptionService subscriptionService;
  @Mock
  PlanRepository planRepository;
  @Mock
  BusinessRepository businessRepository;
  @Mock
  InvoiceRepository invoiceRepository;
  @Mock
  UserRepository userRepository;
  @Mock
  BictorysClient bictorysClient;
  @Mock
  RolePermissionService permissionService;
  @Mock
  AuditLogService auditLogService;
  @Mock
  EntityManager entityManager;

  BictorysProperties bictorysProperties = new BictorysProperties();
  SubscriptionCheckoutService service;
  ObjectMapper mapper = new ObjectMapper();

  UUID businessId = UUID.randomUUID();
  UUID planId = UUID.randomUUID();
  UUID intentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    bictorysProperties.setEnabled(true);
    service = new SubscriptionCheckoutService(
        intentRepository,
        subscriptionService,
        planRepository,
        businessRepository,
        invoiceRepository,
        userRepository,
        bictorysClient,
        bictorysProperties,
        permissionService,
        auditLogService,
        mapper,
        entityManager,
        "http://localhost:5173");
  }

  @Test
  void getCheckoutStatus_checksOwnershipWithoutLoadingIntentBeforeLock() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findBusinessIdById(intentId)).thenReturn(Optional.of(businessId));
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    when(intentRepository.findById(intentId)).thenReturn(Optional.of(intent));
    when(bictorysClient.getStatus("tx_1")).thenReturn(new BictorysStatusResult("pending", null));
    Plan plan = new Plan();
    plan.setId(planId);
    plan.setSlug("pro");
    when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

    service.getCheckoutStatus(intentId, principal());

    InOrder order = inOrder(intentRepository);
    order.verify(intentRepository).findBusinessIdById(intentId);
    order.verify(intentRepository).findByIdForUpdate(intentId);
    order.verify(intentRepository).findById(intentId);
  }

  @Test
  void getCheckoutStatus_intentAlreadyPaidByWebhook_doesNotActivateAgain() {
    SubscriptionPaymentIntent intent = pendingIntent();
    intent.markPaid();
    when(intentRepository.findBusinessIdById(intentId)).thenReturn(Optional.of(businessId));
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    when(intentRepository.findById(intentId)).thenReturn(Optional.of(intent));
    Plan plan = new Plan();
    plan.setId(planId);
    plan.setSlug("pro");
    when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

    service.getCheckoutStatus(intentId, principal());

    verify(bictorysClient, never()).getStatus(any());
    verify(subscriptionService, never()).activatePaidPlan(any(), any(), any());
  }

  @Test
  void getCheckoutStatus_otherBusiness_isDenied() {
    when(intentRepository.findBusinessIdById(intentId)).thenReturn(Optional.of(UUID.randomUUID()));

    assertThatThrownBy(() -> service.getCheckoutStatus(intentId, principal()))
        .isInstanceOf(AccessDeniedException.class);
    verify(intentRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void lockedIntent_isRefreshedWhenAlreadyManaged() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    when(entityManager.contains(intent)).thenReturn(true);
    when(intentRepository.save(any(SubscriptionPaymentIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.handleBictorysWebhook(webhook("failed", 25000));

    verify(entityManager).refresh(intent, LockModeType.PESSIMISTIC_WRITE);
  }

  @Test
  void handleBictorysWebhook_succeeded_activatesOnce_andIsIdempotent() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));

    Plan plan = new Plan();
    plan.setId(planId);
    plan.setSlug("pro");
    plan.setName("Pro");
    when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

    Subscription sub = new Subscription();
    sub.setId(UUID.randomUUID());
    sub.setBusinessId(businessId);
    sub.setPlanId(planId);
    sub.setBillingCycle("monthly");
    sub.setStatus(SubscriptionStatus.ACTIVE);
    sub.setCurrentPeriodStart(LocalDate.now());
    sub.setCurrentPeriodEnd(LocalDate.now().plusMonths(1));
    when(subscriptionService.activatePaidPlan(businessId, "pro", "monthly")).thenReturn(sub);

    Invoice savedInvoice = new Invoice();
    savedInvoice.setId(UUID.randomUUID());
    savedInvoice.setNumber("SUB-1");
    when(invoiceRepository.save(any(Invoice.class))).thenReturn(savedInvoice);
    when(intentRepository.save(any(SubscriptionPaymentIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ObjectNode payload = webhook("succeeded", 25000);

    service.handleBictorysWebhook(payload);
    service.handleBictorysWebhook(payload);

    verify(subscriptionService, times(1)).activatePaidPlan(businessId, "pro", "monthly");
    verify(invoiceRepository, times(1)).save(any(Invoice.class));
    assertThat(intent.getStatus()).isEqualTo(SubscriptionPaymentStatus.PAID);
    assertThat(intent.getSubscriptionId()).isEqualTo(sub.getId());
    assertThat(intent.getInvoiceId()).isEqualTo(savedInvoice.getId());
  }

  @Test
  void handleBictorysWebhook_rejectsAmountMismatch() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    when(intentRepository.save(any(SubscriptionPaymentIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> service.handleBictorysWebhook(webhook("succeeded", 999)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Montant");
    verify(subscriptionService, never()).activatePaidPlan(any(), any(), any());
    assertThat(intent.getStatus()).isEqualTo(SubscriptionPaymentStatus.FAILED);
  }

  @Test
  void handleBictorysWebhook_failed_marksIntentFailed() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
    when(intentRepository.save(any(SubscriptionPaymentIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.handleBictorysWebhook(webhook("cancelled", 25000));

    verify(subscriptionService, never()).activatePaidPlan(any(), any(), any());
    assertThat(intent.getStatus()).isEqualTo(SubscriptionPaymentStatus.FAILED);
  }

  @Test
  void handleBictorysWebhook_fallsBackToTransactionIdLookup() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findByExternalTokenForUpdate("tx_1")).thenReturn(Optional.of(intent));
    when(intentRepository.save(any(SubscriptionPaymentIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ObjectNode payload = webhook("failed", 25000);
    payload.put("paymentReference", "not-a-uuid");

    service.handleBictorysWebhook(payload);

    assertThat(intent.getStatus()).isEqualTo(SubscriptionPaymentStatus.FAILED);
  }

  @Test
  void handleBictorysWebhook_unknownIntent_throwsForRetry() {
    when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.empty());
    when(intentRepository.findByExternalTokenForUpdate("tx_1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.handleBictorysWebhook(webhook("succeeded", 25000)))
        .isInstanceOf(PaymentIntentNotFoundException.class);
  }

  @Test
  void fitCheckoutUrl_prefersLinkThenRedirectWithinColumnLimit() {
    String longLink = "https://pay.example/" + "a".repeat(1000);

    assertThat(SubscriptionCheckoutService.fitCheckoutUrl("https://l", "https://r"))
        .isEqualTo("https://l");
    assertThat(SubscriptionCheckoutService.fitCheckoutUrl(longLink, "https://r"))
        .isEqualTo("https://r");
    assertThat(SubscriptionCheckoutService.fitCheckoutUrl(null, longLink)).isNull();
  }

  private ObjectNode webhook(String status, int amount) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("id", "tx_1");
    payload.put("type", "payment");
    payload.put("status", status);
    payload.put("amount", amount);
    payload.put("currency", "XOF");
    payload.put("paymentReference", intentId.toString());
    payload.put("pspName", "wave_money");
    return payload;
  }

  private UserPrincipal principal() {
    return new UserPrincipal(UUID.randomUUID(), "owner@test.sn", businessId, "OWNER", null, false);
  }

  private SubscriptionPaymentIntent pendingIntent() {
    SubscriptionPaymentIntent intent = new SubscriptionPaymentIntent();
    intent.setId(intentId);
    intent.setBusinessId(businessId);
    intent.setPlanId(planId);
    intent.setBillingCycle("monthly");
    intent.setAmount(25000);
    intent.setCurrency("XOF");
    intent.setProvider("bictorys");
    intent.setPreferredChannel("wave");
    intent.setStatus(SubscriptionPaymentStatus.PENDING);
    intent.setExternalToken("tx_1");
    return intent;
  }
}
