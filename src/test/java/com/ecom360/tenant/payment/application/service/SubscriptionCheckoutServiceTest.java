package com.ecom360.tenant.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.repository.UserRepository;
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
import com.ecom360.tenant.payment.infrastructure.config.PaydunyaProperties;
import com.ecom360.tenant.payment.infrastructure.paydunya.PaydunyaClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  PaydunyaClient paydunyaClient;
  @Mock
  RolePermissionService permissionService;
  @Mock
  AuditLogService auditLogService;

  PaydunyaProperties paydunyaProperties = new PaydunyaProperties();
  SubscriptionCheckoutService service;
  ObjectMapper mapper = new ObjectMapper();

  UUID businessId = UUID.randomUUID();
  UUID planId = UUID.randomUUID();
  UUID intentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    paydunyaProperties.setEnabled(true);
    paydunyaProperties.setMasterKey("master");
    service = new SubscriptionCheckoutService(
        intentRepository,
        subscriptionService,
        planRepository,
        businessRepository,
        invoiceRepository,
        userRepository,
        paydunyaClient,
        paydunyaProperties,
        permissionService,
        auditLogService,
        "http://localhost:5173");
  }

  @Test
  void handlePaydunyaIpn_completed_activatesOnce_andIsIdempotent() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findByExternalTokenForUpdate("tok_1")).thenReturn(Optional.of(intent));
    when(paydunyaClient.verifyMasterKeyHash(any())).thenReturn(true);

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

    ObjectNode data = completedIpn(25000);

    service.handlePaydunyaIpn(data);
    service.handlePaydunyaIpn(data);

    verify(subscriptionService, times(1)).activatePaidPlan(businessId, "pro", "monthly");
    verify(invoiceRepository, times(1)).save(any(Invoice.class));
    assertThat(intent.getStatus()).isEqualTo(SubscriptionPaymentStatus.PAID);
    assertThat(intent.getSubscriptionId()).isEqualTo(sub.getId());
    assertThat(intent.getInvoiceId()).isEqualTo(savedInvoice.getId());
  }

  @Test
  void handlePaydunyaIpn_rejectsAmountMismatch() {
    SubscriptionPaymentIntent intent = pendingIntent();
    when(intentRepository.findByExternalTokenForUpdate("tok_1")).thenReturn(Optional.of(intent));
    when(paydunyaClient.verifyMasterKeyHash(any())).thenReturn(true);
    when(intentRepository.save(any(SubscriptionPaymentIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ObjectNode data = completedIpn(999);

    assertThatThrownBy(() -> service.handlePaydunyaIpn(data))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Montant");
    verify(subscriptionService, never()).activatePaidPlan(any(), any(), any());
    assertThat(intent.getStatus()).isEqualTo(SubscriptionPaymentStatus.FAILED);
  }

  @Test
  void handlePaydunyaIpn_unknownIntent_throwsForRetry() {
    when(paydunyaClient.verifyMasterKeyHash(any())).thenReturn(true);
    when(intentRepository.findByExternalTokenForUpdate("tok_missing")).thenReturn(Optional.empty());

    ObjectNode data = mapper.createObjectNode();
    data.put("hash", "abc");
    data.put("status", "completed");
    ObjectNode invoice = data.putObject("invoice");
    invoice.put("token", "tok_missing");
    invoice.put("total_amount", 25000);

    assertThatThrownBy(() -> service.handlePaydunyaIpn(data))
        .isInstanceOf(PaymentIntentNotFoundException.class);
  }

  @Test
  void handlePaydunyaIpn_rejectsInvalidHash() {
    when(paydunyaClient.verifyMasterKeyHash(eq("bad"))).thenReturn(false);
    ObjectNode data = mapper.createObjectNode();
    data.put("hash", "bad");
    data.put("status", "completed");

    assertThatThrownBy(() -> service.handlePaydunyaIpn(data))
        .hasMessageContaining("Hash");
    verify(subscriptionService, never()).activatePaidPlan(any(), any(), any());
  }

  private ObjectNode completedIpn(int amount) {
    ObjectNode data = mapper.createObjectNode();
    data.put("hash", "abc");
    data.put("status", "completed");
    ObjectNode invoice = data.putObject("invoice");
    invoice.put("token", "tok_1");
    invoice.put("total_amount", amount);
    return data;
  }

  private SubscriptionPaymentIntent pendingIntent() {
    SubscriptionPaymentIntent intent = new SubscriptionPaymentIntent();
    intent.setId(intentId);
    intent.setBusinessId(businessId);
    intent.setPlanId(planId);
    intent.setBillingCycle("monthly");
    intent.setAmount(25000);
    intent.setCurrency("XOF");
    intent.setProvider("paydunya");
    intent.setPreferredChannel("wave");
    intent.setStatus(SubscriptionPaymentStatus.PENDING);
    intent.setExternalToken("tok_1");
    return intent;
  }
}
