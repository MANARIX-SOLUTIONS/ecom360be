package com.ecom360.tenant.payment.application.service;

import com.ecom360.audit.application.service.AuditLogService;
import com.ecom360.identity.application.service.RolePermissionService;
import com.ecom360.identity.domain.model.Permission;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.application.dto.PageResponse;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.ecom360.tenant.application.service.SubscriptionService;
import com.ecom360.tenant.domain.model.Business;
import com.ecom360.tenant.domain.model.Invoice;
import com.ecom360.tenant.domain.model.Plan;
import com.ecom360.tenant.domain.model.Subscription;
import com.ecom360.tenant.domain.repository.BusinessRepository;
import com.ecom360.tenant.domain.repository.InvoiceRepository;
import com.ecom360.tenant.domain.repository.PlanRepository;
import com.ecom360.tenant.payment.application.dto.AdminSubscriptionPaymentResponse;
import com.ecom360.tenant.payment.application.dto.CreateSubscriptionCheckoutRequest;
import com.ecom360.tenant.payment.application.dto.SubscriptionCheckoutResponse;
import com.ecom360.tenant.payment.domain.PaymentIntentNotFoundException;
import com.ecom360.tenant.payment.domain.model.SubscriptionPaymentIntent;
import com.ecom360.tenant.payment.domain.model.SubscriptionPaymentStatus;
import com.ecom360.tenant.payment.domain.repository.SubscriptionPaymentIntentRepository;
import com.ecom360.tenant.payment.infrastructure.config.PaydunyaProperties;
import com.ecom360.tenant.payment.infrastructure.paydunya.PaydunyaCheckoutResult;
import com.ecom360.tenant.payment.infrastructure.paydunya.PaydunyaClient;
import com.ecom360.tenant.payment.infrastructure.paydunya.PaydunyaConfirmResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionCheckoutService {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionCheckoutService.class);

  /**
   * Pending checkout intents older than this are expired by the scheduled job.
   */
  public static final int PENDING_INTENT_TTL_HOURS = 48;

  private final SubscriptionPaymentIntentRepository intentRepository;
  private final SubscriptionService subscriptionService;
  private final PlanRepository planRepository;
  private final BusinessRepository businessRepository;
  private final InvoiceRepository invoiceRepository;
  private final UserRepository userRepository;
  private final PaydunyaClient paydunyaClient;
  private final PaydunyaProperties paydunyaProperties;
  private final RolePermissionService permissionService;
  private final AuditLogService auditLogService;
  private final String appUrl;

  public SubscriptionCheckoutService(
      SubscriptionPaymentIntentRepository intentRepository,
      SubscriptionService subscriptionService,
      PlanRepository planRepository,
      BusinessRepository businessRepository,
      InvoiceRepository invoiceRepository,
      UserRepository userRepository,
      PaydunyaClient paydunyaClient,
      PaydunyaProperties paydunyaProperties,
      RolePermissionService permissionService,
      AuditLogService auditLogService,
      @Value("${app.url:http://localhost:5173}") String appUrl) {
    this.intentRepository = intentRepository;
    this.subscriptionService = subscriptionService;
    this.planRepository = planRepository;
    this.businessRepository = businessRepository;
    this.invoiceRepository = invoiceRepository;
    this.userRepository = userRepository;
    this.paydunyaClient = paydunyaClient;
    this.paydunyaProperties = paydunyaProperties;
    this.permissionService = permissionService;
    this.auditLogService = auditLogService;
    this.appUrl = appUrl;
  }

  @Transactional
  public SubscriptionCheckoutResponse createCheckout(
      CreateSubscriptionCheckoutRequest req, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_UPDATE);

    String channel = normalizeChannel(req.channel());
    String cycle = "yearly".equalsIgnoreCase(req.billingCycle()) ? "yearly" : "monthly";
    Plan plan = subscriptionService.requireActivePlanBySlug(req.planSlug());
    subscriptionService.assertNotAlreadyOnPlan(p.businessId(), plan, cycle);

    int amount = subscriptionService.resolvePlanAmount(plan, cycle);
    Business business = businessRepository
        .findById(p.businessId())
        .orElseThrow(() -> new ResourceNotFoundException("Business", p.businessId()));

    String returnUrl = trimSlash(appUrl) + "/settings/subscription?checkout=";

    SubscriptionPaymentIntent intent = new SubscriptionPaymentIntent();
    intent.setBusinessId(p.businessId());
    intent.setPlanId(plan.getId());
    intent.setBillingCycle(cycle);
    intent.setAmount(amount);
    intent.setCurrency("XOF");
    intent.setProvider("paydunya");
    intent.setPreferredChannel(channel);
    intent.setStatus(SubscriptionPaymentStatus.PENDING);
    intent.setCreatedByUserId(p.userId());
    intent = intentRepository.save(intent);

    returnUrl = returnUrl + intent.getId();
    intent.setReturnUrl(returnUrl);

    User user = userRepository.findById(p.userId()).orElse(null);
    String description = "Abonnement Ecom 360 PME — "
        + plan.getName()
        + " ("
        + cycle
        + ") — "
        + business.getName();

    PaydunyaCheckoutResult checkout;
    try {
      checkout = paydunyaClient.createCheckoutInvoice(
          amount,
          description,
          channel,
          intent.getId(),
          returnUrl,
          returnUrl + "&cancelled=1",
          user != null ? user.getFullName() : business.getName(),
          business.getEmail(),
          business.getPhone());
    } catch (RuntimeException e) {
      intent.markFailed(e.getMessage());
      intentRepository.save(intent);
      throw e;
    }

    intent.setExternalToken(checkout.token());
    intent.setCheckoutUrl(checkout.checkoutUrl());
    intent = intentRepository.save(intent);

    auditLogService.log(
        p.businessId(),
        p.userId(),
        "subscription.checkout.created",
        "SubscriptionPaymentIntent",
        intent.getId(),
        Map.of(
            "planSlug", plan.getSlug(),
            "billingCycle", cycle,
            "channel", channel,
            "amount", amount),
        null);

    return toCheckoutResponse(intent, plan.getSlug());
  }

  @Transactional
  public SubscriptionCheckoutResponse getCheckoutStatus(UUID intentId, UserPrincipal p) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_READ);
    requireIntentForBusiness(intentId, p.businessId());

    if (paydunyaProperties.isEnabled()) {
      trySyncPendingFromPaydunya(intentId, p.userId());
    }

    SubscriptionPaymentIntent intent = intentRepository
        .findById(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId));
    Plan plan = planRepository
        .findById(intent.getPlanId())
        .orElseThrow(() -> new ResourceNotFoundException("Plan", intent.getPlanId()));
    return toCheckoutResponse(intent, plan.getSlug());
  }

  @Transactional(readOnly = true)
  public PageResponse<SubscriptionCheckoutResponse> listTenantPayments(
      UserPrincipal p, int page, int size) {
    requireBiz(p);
    permissionService.require(p, Permission.SUBSCRIPTION_READ);
    Page<SubscriptionPaymentIntent> result = intentRepository.findByBusinessIdOrderByCreatedAtDesc(
        p.businessId(), PageRequest.of(page, size));
    return PageResponse.of(
        result.map(
            intent -> {
              String slug = planRepository
                  .findById(intent.getPlanId())
                  .map(Plan::getSlug)
                  .orElse("unknown");
              return toCheckoutResponse(intent, slug);
            }));
  }

  @Transactional
  public void handlePaydunyaIpn(JsonNode dataNode) {
    if (dataNode == null || dataNode.isNull()) {
      throw new BusinessRuleException("IPN PayDunya invalide");
    }
    String hash = text(dataNode, "hash");
    if (!paydunyaClient.verifyMasterKeyHash(hash)) {
      throw new AccessDeniedException("Hash IPN PayDunya invalide");
    }

    String status = text(dataNode, "status");
    String token = null;
    JsonNode invoiceNode = dataNode.path("invoice");
    if (invoiceNode.isObject()) {
      token = text(invoiceNode, "token");
    }
    if (token == null || token.isBlank()) {
      token = text(dataNode, "token");
    }

    UUID intentId = null;
    JsonNode custom = dataNode.path("custom_data");
    if (custom.isObject()) {
      String raw = text(custom, "intent_id");
      if (raw != null && !raw.isBlank()) {
        try {
          intentId = UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
          // fall through to token lookup
        }
      }
    }

    Integer paidAmount = intOrNull(dataNode, "total_amount");
    if (paidAmount == null && invoiceNode.isObject()) {
      paidAmount = intOrNull(invoiceNode, "total_amount");
    }

    SubscriptionPaymentIntent locked = lockIntent(intentId, token);
    if (locked == null) {
      throw new PaymentIntentNotFoundException(
          "PayDunya IPN: intent not found (token=" + token + ")");
    }

    if (locked.isPaid()) {
      return;
    }

    if (status != null && "completed".equalsIgnoreCase(status)) {
      if (token != null) {
        locked.setExternalToken(token);
      }
      fulfillLockedIntent(locked, "paydunya_ipn", null, null, paidAmount);
      return;
    }

    if (status != null) {
      String s = status.toLowerCase();
      if ("failed".equals(s) || "cancelled".equals(s) || "canceled".equals(s)) {
        if (locked.isPending()) {
          locked.markFailed(
              text(dataNode, "fail_reason") != null ? text(dataNode, "fail_reason") : status);
          intentRepository.save(locked);
        }
      }
    }
  }

  @Transactional
  public AdminSubscriptionPaymentResponse markPaid(
      UUID intentId, String note, UserPrincipal admin) {
    SubscriptionPaymentIntent locked = intentRepository
        .findByIdForUpdate(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId));
    if (locked.isPaid()) {
      return toAdminResponse(locked);
    }
    if (!locked.isPending()) {
      throw new BusinessRuleException(
          "Seules les intentions en attente peuvent être marquées payées");
    }
    fulfillLockedIntent(locked, "admin_mark_paid", admin.userId(), note, locked.getAmount());
    return toAdminResponse(locked);
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminSubscriptionPaymentResponse> listAdminPayments(
      UUID businessId, String status, Instant from, Instant to, int page, int size) {
    Page<SubscriptionPaymentIntent> result = intentRepository.search(
        businessId,
        status != null && !status.isBlank() ? status : null,
        from,
        to,
        PageRequest.of(page, size));
    return PageResponse.of(result.map(this::toAdminResponse));
  }

  /** Expire abandoned pending intents (scheduled). */
  @Transactional
  public int expireStalePendingIntents() {
    Instant cutoff = Instant.now().minusSeconds(PENDING_INTENT_TTL_HOURS * 3600L);
    List<SubscriptionPaymentIntent> stale = intentRepository.findByStatusAndCreatedAtBefore(
        SubscriptionPaymentStatus.PENDING, cutoff);
    for (SubscriptionPaymentIntent intent : stale) {
      intent.markExpired("Checkout expiré après " + PENDING_INTENT_TTL_HOURS + "h sans paiement");
      intentRepository.save(intent);
      log.info("Expired stale payment intent {} (business={})", intent.getId(), intent.getBusinessId());
    }
    return stale.size();
  }

  private void trySyncPendingFromPaydunya(UUID intentId, UUID actorUserId) {
    SubscriptionPaymentIntent locked = intentRepository.findByIdForUpdate(intentId).orElse(null);
    if (locked == null || !locked.isPending() || locked.getExternalToken() == null) {
      return;
    }
    try {
      PaydunyaConfirmResult confirm = paydunyaClient.confirmInvoice(locked.getExternalToken());
      if (!paydunyaClient.verifyMasterKeyHash(confirm.hash())) {
        log.warn("PayDunya confirm hash invalid for intent {}", intentId);
        return;
      }
      if (confirm.isCompleted()) {
        fulfillLockedIntent(
            locked, "paydunya_confirm", actorUserId, null, confirm.totalAmount());
      } else if (confirm.isFailedOrCancelled()) {
        locked.markFailed(
            confirm.failReason() != null ? confirm.failReason() : confirm.status());
        intentRepository.save(locked);
      }
    } catch (BusinessRuleException e) {
      log.debug("Confirm poll skipped: {}", e.getMessage());
    }
  }

  private SubscriptionPaymentIntent lockIntent(UUID intentId, String token) {
    if (intentId != null) {
      SubscriptionPaymentIntent byId = intentRepository.findByIdForUpdate(intentId).orElse(null);
      if (byId != null) {
        return byId;
      }
    }
    if (token != null && !token.isBlank()) {
      return intentRepository.findByExternalTokenForUpdate(token).orElse(null);
    }
    return null;
  }

  /**
   * Must be called with a pessimistically locked pending (or already-paid) intent
   * in the same
   * transaction.
   */
  private void fulfillLockedIntent(
      SubscriptionPaymentIntent intent,
      String source,
      UUID actorUserId,
      String note,
      Integer paidAmount) {
    if (intent.isPaid()) {
      return;
    }
    if (!intent.isPending()) {
      throw new BusinessRuleException(
          "Impossible d'activer : intention en statut " + intent.getStatus());
    }

    if (paidAmount != null && !paidAmount.equals(intent.getAmount())) {
      String msg = "Montant PayDunya ("
          + paidAmount
          + ") différent de l'intention ("
          + intent.getAmount()
          + ")";
      intent.markFailed(msg);
      intentRepository.save(intent);
      log.error("Payment amount mismatch intent={} {}", intent.getId(), msg);
      throw new BusinessRuleException(msg);
    }
    if (paidAmount == null && !"admin_mark_paid".equals(source)) {
      log.warn(
          "Payment completed without total_amount for intent={} — proceeding with intent amount {}",
          intent.getId(),
          intent.getAmount());
    }

    Plan plan = planRepository
        .findById(intent.getPlanId())
        .orElseThrow(() -> new ResourceNotFoundException("Plan", intent.getPlanId()));

    Subscription subscription = subscriptionService.activatePaidPlan(
        intent.getBusinessId(), plan.getSlug(), intent.getBillingCycle());

    Invoice invoice = new Invoice();
    invoice.setBusinessId(intent.getBusinessId());
    invoice.setSubscriptionId(subscription.getId());
    invoice.setNumber(generateInvoiceNumber(intent.getBusinessId()));
    invoice.setAmount(intent.getAmount());
    invoice.setStatus("paid");
    invoice.setPaymentMethod(intent.getPreferredChannel());
    invoice.setPaymentIntentId(intent.getId());
    invoice.setProvider(intent.getProvider());
    invoice.setExternalRef(intent.getExternalToken());
    invoice.setDueDate(LocalDate.now());
    invoice.setPaidAt(LocalDate.now());
    invoice = invoiceRepository.save(invoice);

    intent.markPaid();
    intent.setSubscriptionId(subscription.getId());
    intent.setInvoiceId(invoice.getId());
    if (intent.getExternalRef() == null) {
      intent.setExternalRef(intent.getExternalToken());
    }
    intentRepository.save(intent);

    Map<String, Object> changes = new HashMap<>();
    changes.put("source", source);
    changes.put("planSlug", plan.getSlug());
    changes.put("billingCycle", intent.getBillingCycle());
    changes.put("amount", intent.getAmount());
    if (paidAmount != null) {
      changes.put("paidAmount", paidAmount);
    }
    changes.put("channel", intent.getPreferredChannel());
    changes.put("subscriptionId", subscription.getId().toString());
    changes.put("invoiceId", invoice.getId().toString());
    if (note != null && !note.isBlank()) {
      changes.put("note", note);
    }

    auditLogService.log(
        intent.getBusinessId(),
        actorUserId,
        "subscription.payment.paid",
        "SubscriptionPaymentIntent",
        intent.getId(),
        changes,
        null);

    log.info(
        "Subscription payment fulfilled intent={} business={} source={}",
        intent.getId(),
        intent.getBusinessId(),
        source);
  }

  private String generateInvoiceNumber(UUID businessId) {
    String suffix = businessId.toString().replace("-", "").substring(0, 8).toUpperCase();
    String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return "SUB-" + LocalDate.now() + "-" + suffix + "-" + unique;
  }

  private SubscriptionPaymentIntent requireIntentForBusiness(UUID intentId, UUID businessId) {
    SubscriptionPaymentIntent intent = intentRepository
        .findById(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId));
    if (!intent.getBusinessId().equals(businessId)) {
      throw new AccessDeniedException("Payment intent does not belong to this business");
    }
    return intent;
  }

  private SubscriptionCheckoutResponse toCheckoutResponse(
      SubscriptionPaymentIntent intent, String planSlug) {
    return new SubscriptionCheckoutResponse(
        intent.getId(),
        intent.getStatus(),
        intent.getCheckoutUrl(),
        intent.getAmount(),
        intent.getCurrency(),
        planSlug,
        intent.getBillingCycle(),
        intent.getPreferredChannel(),
        intent.getProvider(),
        intent.getSubscriptionId(),
        intent.getInvoiceId(),
        intent.getFailureReason(),
        intent.getPaidAt());
  }

  private AdminSubscriptionPaymentResponse toAdminResponse(SubscriptionPaymentIntent intent) {
    Business biz = businessRepository.findById(intent.getBusinessId()).orElse(null);
    Plan plan = planRepository.findById(intent.getPlanId()).orElse(null);
    String invoiceNumber = null;
    if (intent.getInvoiceId() != null) {
      invoiceNumber = invoiceRepository.findById(intent.getInvoiceId()).map(Invoice::getNumber).orElse(null);
    }
    return new AdminSubscriptionPaymentResponse(
        intent.getId(),
        intent.getBusinessId(),
        biz != null ? biz.getName() : null,
        intent.getPlanId(),
        plan != null ? plan.getSlug() : null,
        plan != null ? plan.getName() : null,
        intent.getBillingCycle(),
        intent.getAmount(),
        intent.getCurrency(),
        intent.getProvider(),
        intent.getPreferredChannel(),
        intent.getStatus(),
        intent.getExternalToken(),
        intent.getExternalRef(),
        intent.getCheckoutUrl(),
        intent.getSubscriptionId(),
        intent.getInvoiceId(),
        invoiceNumber,
        intent.getFailureReason(),
        intent.getPaidAt(),
        intent.getCreatedAt(),
        intent.getCreatedByUserId());
  }

  private static String normalizeChannel(String channel) {
    String c = channel.trim().toLowerCase();
    if ("wave".equals(c)) {
      return "wave";
    }
    if ("orange_money".equals(c) || "orange-money".equals(c) || "om".equals(c)) {
      return "orange_money";
    }
    throw new BusinessRuleException("Canal de paiement invalide (wave ou orange_money)");
  }

  private static String trimSlash(String url) {
    if (url == null || url.isBlank()) {
      return "http://localhost:5173";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String text(JsonNode node, String field) {
    JsonNode n = node.get(field);
    return n == null || n.isNull() ? null : n.asText();
  }

  private static Integer intOrNull(JsonNode node, String field) {
    JsonNode n = node.get(field);
    if (n == null || n.isNull()) {
      return null;
    }
    if (n.isNumber()) {
      return n.asInt();
    }
    try {
      return Integer.parseInt(n.asText().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void requireBiz(UserPrincipal p) {
    if (!p.hasBusinessAccess()) {
      throw new AccessDeniedException("Business context required");
    }
  }

  public static Instant parseInstantOrDate(String raw, boolean endOfDay) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (Exception ignored) {
      LocalDate d = LocalDate.parse(raw);
      if (endOfDay) {
        return d.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1);
      }
      return d.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
  }
}
