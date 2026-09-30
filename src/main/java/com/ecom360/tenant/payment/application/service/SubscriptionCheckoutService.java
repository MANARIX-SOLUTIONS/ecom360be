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
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysChargeResult;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysClient;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysProperties;
import com.ecom360.tenant.payment.infrastructure.bictorys.BictorysStatusResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

  public static final String PROVIDER = "bictorys";

  private static final int CHECKOUT_URL_MAX = 1000;

  private final SubscriptionPaymentIntentRepository intentRepository;
  private final SubscriptionService subscriptionService;
  private final PlanRepository planRepository;
  private final BusinessRepository businessRepository;
  private final InvoiceRepository invoiceRepository;
  private final UserRepository userRepository;
  private final BictorysClient bictorysClient;
  private final BictorysProperties bictorysProperties;
  private final RolePermissionService permissionService;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;
  private final EntityManager entityManager;
  private final String appUrl;

  public SubscriptionCheckoutService(
      SubscriptionPaymentIntentRepository intentRepository,
      SubscriptionService subscriptionService,
      PlanRepository planRepository,
      BusinessRepository businessRepository,
      InvoiceRepository invoiceRepository,
      UserRepository userRepository,
      BictorysClient bictorysClient,
      BictorysProperties bictorysProperties,
      RolePermissionService permissionService,
      AuditLogService auditLogService,
      ObjectMapper objectMapper,
      EntityManager entityManager,
      @Value("${app.url:http://localhost:5173}") String appUrl) {
    this.intentRepository = intentRepository;
    this.subscriptionService = subscriptionService;
    this.planRepository = planRepository;
    this.businessRepository = businessRepository;
    this.invoiceRepository = invoiceRepository;
    this.userRepository = userRepository;
    this.bictorysClient = bictorysClient;
    this.bictorysProperties = bictorysProperties;
    this.permissionService = permissionService;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
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

    String redirectBase = bictorysProperties.getRedirectBaseUrl();
    String returnUrl = trimSlash(redirectBase == null || redirectBase.isBlank() ? appUrl : redirectBase)
        + "/settings/subscription?checkout=";

    SubscriptionPaymentIntent intent = new SubscriptionPaymentIntent();
    intent.setBusinessId(p.businessId());
    intent.setPlanId(plan.getId());
    intent.setBillingCycle(cycle);
    intent.setAmount(amount);
    intent.setCurrency("XOF");
    intent.setProvider(PROVIDER);
    intent.setPreferredChannel(channel);
    intent.setStatus(SubscriptionPaymentStatus.PENDING);
    intent.setCreatedByUserId(p.userId());
    intent = intentRepository.save(intent);

    returnUrl = returnUrl + intent.getId();
    intent.setReturnUrl(returnUrl);

    User user = userRepository.findById(p.userId()).orElse(null);

    BictorysChargeResult charge;
    try {
      charge = bictorysClient.createCharge(
          amount,
          channel,
          intent.getId().toString(),
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

    intent.setExternalToken(charge.transactionId());
    intent.setCheckoutUrl(fitCheckoutUrl(charge.link(), charge.redirectUrl()));
    intent.setMetadata(buildChargeMetadata(charge));
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
    requireIntentOwnership(intentId, p.businessId());

    if (bictorysProperties.isEnabled()) {
      trySyncPendingFromBictorys(intentId, p.userId());
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

  /**
   * Handles a Bictorys webhook payload. The caller must have verified the
   * signature ({@link BictorysClient#verifyWebhook}) beforehand.
   */
  @Transactional
  public void handleBictorysWebhook(JsonNode payload) {
    if (payload == null || payload.isNull() || !payload.isObject()) {
      throw new BusinessRuleException("Webhook Bictorys invalide");
    }
    String type = text(payload, "type");
    if (type != null && !"payment".equalsIgnoreCase(type)) {
      log.info("Bictorys webhook ignored (type={})", type);
      return;
    }

    String status = text(payload, "status");
    String transactionId = text(payload, "id");
    String reference = text(payload, "paymentReference");
    if (reference == null || reference.isBlank()) {
      reference = text(payload, "merchantReference");
    }

    UUID intentId = null;
    if (reference != null && !reference.isBlank()) {
      try {
        intentId = UUID.fromString(reference.trim());
      } catch (IllegalArgumentException ignored) {
        // fall through to transaction id lookup
      }
    }

    SubscriptionPaymentIntent locked = lockIntent(intentId, transactionId);
    if (locked == null) {
      throw new PaymentIntentNotFoundException(
          "Bictorys webhook: intent not found (ref=" + reference + ", id=" + transactionId + ")");
    }

    if (locked.isPaid()) {
      return;
    }

    if (BictorysStatusResult.isSucceeded(status)) {
      String currency = text(payload, "currency");
      if (currency != null && !currency.equalsIgnoreCase(locked.getCurrency())) {
        String msg = "Devise du paiement (" + currency + ") différente de l'intention ("
            + locked.getCurrency() + ")";
        locked.markFailed(msg);
        intentRepository.save(locked);
        throw new BusinessRuleException(msg);
      }
      if (transactionId != null) {
        locked.setExternalToken(transactionId);
      }
      fulfillLockedIntent(
          locked, "bictorys_webhook", null, null, intOrNull(payload, "amount"));
      return;
    }

    if (BictorysStatusResult.isFailed(status) && locked.isPending()) {
      locked.markFailed("Paiement " + status);
      intentRepository.save(locked);
    }
  }

  @Transactional
  public AdminSubscriptionPaymentResponse markPaid(
      UUID intentId, String note, UserPrincipal admin) {
    SubscriptionPaymentIntent locked = fresh(intentRepository
        .findByIdForUpdate(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId)));
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

  private void trySyncPendingFromBictorys(UUID intentId, UUID actorUserId) {
    SubscriptionPaymentIntent locked = fresh(intentRepository.findByIdForUpdate(intentId).orElse(null));
    if (locked == null
        || !locked.isPending()
        || locked.getExternalToken() == null
        || !PROVIDER.equals(locked.getProvider())) {
      return;
    }
    try {
      BictorysStatusResult result = bictorysClient.getStatus(locked.getExternalToken());
      if (result.isSucceeded()) {
        fulfillLockedIntent(locked, "bictorys_status", actorUserId, null, result.amount());
      } else if (result.isFailed()) {
        locked.markFailed("Paiement " + result.status());
        intentRepository.save(locked);
      }
    } catch (BusinessRuleException e) {
      log.debug("Status poll skipped: {}", e.getMessage());
    }
  }

  /** {@code checkout_url} is VARCHAR(1000); the full link stays in metadata. */
  static String fitCheckoutUrl(String link, String redirectUrl) {
    for (String candidate : new String[] { link, redirectUrl }) {
      if (candidate != null && !candidate.isBlank() && candidate.length() <= CHECKOUT_URL_MAX) {
        return candidate;
      }
    }
    return null;
  }

  private String buildChargeMetadata(BictorysChargeResult charge) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("transactionId", charge.transactionId());
    node.put("redirectUrl", charge.redirectUrl());
    node.put("link", charge.link());
    node.put("qrCode", charge.qrCode());
    node.put("message", charge.message());
    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      log.warn("Unable to serialize charge metadata: {}", e.getMessage());
      return null;
    }
  }

  private JsonNode readMetadata(SubscriptionPaymentIntent intent) {
    if (intent.getMetadata() == null || intent.getMetadata().isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(intent.getMetadata());
    } catch (Exception e) {
      return null;
    }
  }

  private SubscriptionPaymentIntent lockIntent(UUID intentId, String token) {
    if (intentId != null) {
      SubscriptionPaymentIntent byId = intentRepository.findByIdForUpdate(intentId).orElse(null);
      if (byId != null) {
        return fresh(byId);
      }
    }
    if (token != null && !token.isBlank()) {
      return fresh(intentRepository.findByExternalTokenForUpdate(token).orElse(null));
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
      String msg = "Montant payé ("
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

  /**
   * Must not load the entity: a managed PENDING copy would be returned as-is by
   * the later SELECT ... FOR UPDATE, even after a concurrent webhook marked it paid.
   */
  private void requireIntentOwnership(UUID intentId, UUID businessId) {
    UUID owner = intentRepository
        .findBusinessIdById(intentId)
        .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId));
    if (!owner.equals(businessId)) {
      throw new AccessDeniedException("Payment intent does not belong to this business");
    }
  }

  /** Re-reads the row under the lock so the state checked is never a stale session copy. */
  private SubscriptionPaymentIntent fresh(SubscriptionPaymentIntent intent) {
    if (intent != null && entityManager.contains(intent)) {
      entityManager.refresh(intent, LockModeType.PESSIMISTIC_WRITE);
    }
    return intent;
  }

  private SubscriptionCheckoutResponse toCheckoutResponse(
      SubscriptionPaymentIntent intent, String planSlug) {
    String qrCode = null;
    String paymentLink = null;
    String ussdMessage = null;
    if (intent.isPending()) {
      JsonNode meta = readMetadata(intent);
      if (meta != null) {
        qrCode = text(meta, "qrCode");
        paymentLink = text(meta, "link");
        ussdMessage = text(meta, "message");
      }
    }
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
        intent.getPaidAt(),
        qrCode,
        paymentLink,
        ussdMessage);
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
