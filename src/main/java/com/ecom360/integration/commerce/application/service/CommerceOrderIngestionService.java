package com.ecom360.integration.commerce.application.service;

import com.ecom360.catalog.domain.model.Product;
import com.ecom360.catalog.domain.repository.ProductRepository;
import com.ecom360.integration.commerce.application.dto.CanonicalOrderLinePayload;
import com.ecom360.integration.commerce.application.dto.CanonicalOrderPayload;
import com.ecom360.integration.commerce.application.dto.WebhookIngestionResponse;
import com.ecom360.integration.commerce.domain.model.CommerceConnection;
import com.ecom360.integration.commerce.domain.model.CommerceOrderIngestionLog;
import com.ecom360.integration.commerce.domain.model.CommerceOrderIngestionStatus;
import com.ecom360.integration.commerce.domain.repository.CommerceConnectionRepository;
import com.ecom360.integration.commerce.domain.repository.CommerceOrderIngestionLogRepository;
import com.ecom360.integration.commerce.woocommerce.WooCommerceOrderMapper;
import com.ecom360.sales.application.dto.ImportedSaleLine;
import com.ecom360.sales.application.dto.SaleResponse;
import com.ecom360.sales.application.service.SaleService;
import com.ecom360.shared.domain.exception.AccessDeniedException;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.ecom360.shared.domain.exception.DomainException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommerceOrderIngestionService {

  private static final int MAX_RAW_PAYLOAD_STORED = 12_000;
  private static final String IMPORT_PAYMENT_METHOD = "web";

  private final CommerceConnectionRepository connectionRepository;
  private final CommerceOrderIngestionLogRepository ingestionLogRepository;
  private final CommerceIngestionFailureLogger failureLogger;
  private final CommerceWebhookHmacVerifier hmacVerifier;
  private final CommerceIntegrationActingUserResolver actingUserResolver;
  private final WooCommerceOrderMapper wooCommerceOrderMapper;
  private final ProductRepository productRepository;
  private final SaleService saleService;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  public CommerceOrderIngestionService(
      CommerceConnectionRepository connectionRepository,
      CommerceOrderIngestionLogRepository ingestionLogRepository,
      CommerceIngestionFailureLogger failureLogger,
      CommerceWebhookHmacVerifier hmacVerifier,
      CommerceIntegrationActingUserResolver actingUserResolver,
      WooCommerceOrderMapper wooCommerceOrderMapper,
      ProductRepository productRepository,
      SaleService saleService,
      ObjectMapper objectMapper,
      Validator validator) {
    this.connectionRepository = connectionRepository;
    this.ingestionLogRepository = ingestionLogRepository;
    this.failureLogger = failureLogger;
    this.hmacVerifier = hmacVerifier;
    this.actingUserResolver = actingUserResolver;
    this.wooCommerceOrderMapper = wooCommerceOrderMapper;
    this.productRepository = productRepository;
    this.saleService = saleService;
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  @Transactional
  public WebhookIngestionResponse ingest(
      String incomingToken, String rawBody, String signatureHeader) {
    CommerceConnection connection =
        connectionRepository
            .findByIncomingToken(incomingToken)
            .orElseThrow(() -> new ResourceNotFoundException("CommerceConnection", "token"));

    hmacVerifier.verify(connection.getHmacSecret(), rawBody, signatureHeader);

    if (!Boolean.TRUE.equals(connection.getIsActive())) {
      throw new AccessDeniedException("Connexion commerce désactivée.");
    }

    String payloadHash = sha256Hex(rawBody);
    String storedRaw = truncate(rawBody, MAX_RAW_PAYLOAD_STORED);

    CanonicalOrderPayload payload;
    try {
      payload = objectMapper.readValue(rawBody, CanonicalOrderPayload.class);
    } catch (JsonProcessingException e) {
      throw new DomainException("Corps JSON invalide ou illisible.");
    }

    Set<ConstraintViolation<CanonicalOrderPayload>> violations = validator.validate(payload);
    if (!violations.isEmpty()) {
      String detail =
          violations.stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .collect(Collectors.joining("; "));
      failureLogger.logValidationFailure(connection, payload, payloadHash, storedRaw, detail);
      throw new BusinessRuleException("Validation du modèle canonique: " + detail);
    }

    return processCanonicalIngestion(connection, payloadHash, storedRaw, payload);
  }

  /**
   * Webhook au format commande WooCommerce (REST v3 / webhook « Order »). Même HMAC que l’endpoint
   * canonique (corps brut WooCommerce). Connexion attendue : {@code sourceType = WOOCOMMERCE}.
   */
  @Transactional
  public WebhookIngestionResponse ingestWooCommerce(
      String incomingToken, String rawWooBody, String signatureHeader) {
    CommerceConnection connection =
        connectionRepository
            .findByIncomingToken(incomingToken)
            .orElseThrow(() -> new ResourceNotFoundException("CommerceConnection", "token"));

    hmacVerifier.verify(connection.getHmacSecret(), rawWooBody, signatureHeader);

    if (!Boolean.TRUE.equals(connection.getIsActive())) {
      throw new AccessDeniedException("Connexion commerce désactivée.");
    }

    if (!"WOOCOMMERCE".equalsIgnoreCase(connection.getSourceType())) {
      throw new BusinessRuleException(
          "Le chemin WooCommerce est réservé aux connexions avec sourceType WOOCOMMERCE (actuel : "
              + connection.getSourceType()
              + ").");
    }

    String payloadHash = sha256Hex(rawWooBody);
    String storedRaw = truncate(rawWooBody, MAX_RAW_PAYLOAD_STORED);

    CanonicalOrderPayload payload;
    try {
      payload = wooCommerceOrderMapper.toCanonical(rawWooBody);
    } catch (JsonProcessingException e) {
      throw new DomainException("JSON WooCommerce invalide ou illisible.");
    }

    Set<ConstraintViolation<CanonicalOrderPayload>> violations = validator.validate(payload);
    if (!violations.isEmpty()) {
      String detail =
          violations.stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .collect(Collectors.joining("; "));
      failureLogger.logValidationFailure(connection, payload, payloadHash, storedRaw, detail);
      throw new BusinessRuleException("Validation du modèle canonique (WooCommerce): " + detail);
    }

    return processCanonicalIngestion(connection, payloadHash, storedRaw, payload);
  }

  private WebhookIngestionResponse processCanonicalIngestion(
      CommerceConnection connection,
      String payloadHash,
      String storedRaw,
      CanonicalOrderPayload payload) {

    assertSourceTypeMatches(connection, payload, payloadHash, storedRaw);

    for (CanonicalOrderLinePayload line : payload.lines()) {
      if (line.productId() == null && (line.sku() == null || line.sku().isBlank())) {
        failureLogger.logValidationFailure(
            connection,
            payload,
            payloadHash,
            storedRaw,
            "Chaque ligne doit avoir un SKU ou un productId.");
        throw new BusinessRuleException("Chaque ligne doit avoir un SKU ou un productId.");
      }
    }

    if (!isPaidForImport(payload.paymentStatus())) {
      failureLogger.logValidationFailure(
          connection,
          payload,
          payloadHash,
          storedRaw,
          "Paiement non confirmé pour création de vente (statut: " + payload.paymentStatus() + ").");
      throw new BusinessRuleException(
          "Statut de paiement non éligible pour créer une vente (attendu: payé / completed / processing, etc.). Statut reçu : "
              + payload.paymentStatus());
    }

    var priorSuccess =
        ingestionLogRepository.findSuccessfulIngestions(
            connection.getId(), payload.externalOrderId());
    if (!priorSuccess.isEmpty()) {
      var first = priorSuccess.get(0);
      return new WebhookIngestionResponse(
          CommerceOrderIngestionStatus.DUPLICATE_SKIPPED.name(),
          first.getId(),
          payload.externalOrderId(),
          "Commande déjà traitée (idempotence).",
          first.getSaleId());
    }

    List<ImportedSaleLine> importedLines = new ArrayList<>();
    for (CanonicalOrderLinePayload line : payload.lines()) {
      Product product = resolveProduct(connection.getBusinessId(), connection.getStoreId(), line);
      importedLines.add(
          new ImportedSaleLine(
              product.getId(), line.label(), line.quantity(), line.unitPriceMinorUnits()));
    }

    UUID actingUserId = actingUserResolver.resolveActingUserId(connection.getBusinessId());
    String note = buildImportNote(payload);
    SaleResponse sale =
        saleService.createSaleFromImport(
            connection.getBusinessId(),
            connection.getStoreId(),
            actingUserId,
            IMPORT_PAYMENT_METHOD,
            0,
            note,
            importedLines);

    CommerceOrderIngestionLog log = new CommerceOrderIngestionLog();
    log.setConnectionId(connection.getId());
    log.setBusinessId(connection.getBusinessId());
    log.setSourceType(payload.sourceType());
    log.setExternalOrderId(payload.externalOrderId());
    log.setStatus(CommerceOrderIngestionStatus.PROCESSED.name());
    log.setPayloadHash(payloadHash);
    log.setRawPayload(storedRaw);
    log.setSaleId(sale.id());
    log = ingestionLogRepository.save(log);

    return new WebhookIngestionResponse(
        CommerceOrderIngestionStatus.PROCESSED.name(),
        log.getId(),
        payload.externalOrderId(),
        "Vente enregistrée.",
        sale.id());
  }

  private static String buildImportNote(CanonicalOrderPayload payload) {
    String base =
        "Import web — commande externe "
            + payload.externalOrderId()
            + " ("
            + payload.sourceType()
            + ")";
    if (base.length() > 3500) {
      return base.substring(0, 3500);
    }
    return base;
  }

  private Product resolveProduct(UUID businessId, UUID storeId, CanonicalOrderLinePayload line) {
    if (line.productId() != null) {
      Product p =
          productRepository
              .findByBusinessIdAndId(businessId, line.productId())
              .orElseThrow(
                  () ->
                      new BusinessRuleException(
                          "Produit introuvable pour productId « " + line.productId() + " »."));
      if (!p.getStoreId().equals(storeId)) {
        throw new BusinessRuleException(
            "Le productId ne correspond pas à la boutique de cette connexion commerce.");
      }
      if (!Boolean.TRUE.equals(p.getIsActive())) {
        throw new BusinessRuleException("Produit inactif : " + line.productId());
      }
      return p;
    }
    return productRepository
        .findByBusinessIdAndStoreIdAndSkuNormalized(businessId, storeId, line.sku().trim())
        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    "Aucun produit actif pour le SKU « "
                        + line.sku()
                        + " » dans la boutique liée à la connexion."));
  }

  private static boolean isPaidForImport(String paymentStatus) {
    if (paymentStatus == null) return false;
    String s = paymentStatus.trim().toLowerCase(Locale.ROOT);
    return switch (s) {
      case "paid",
          "completed",
          "captured",
          "settled",
          "paid_in_full",
          "processing" -> true;
      default -> false;
    };
  }

  private void assertSourceTypeMatches(
      CommerceConnection connection,
      CanonicalOrderPayload payload,
      String payloadHash,
      String storedRaw) {
    if ("GENERIC_WEBHOOK".equalsIgnoreCase(connection.getSourceType())) {
      return;
    }
    if (!connection.getSourceType().equalsIgnoreCase(payload.sourceType())) {
      failureLogger.logValidationFailure(
          connection,
          payload,
          payloadHash,
          storedRaw,
          "sourceType ne correspond pas au type configuré pour cette connexion.");
      throw new BusinessRuleException(
          "Le champ sourceType ne correspond pas au type configuré pour cette connexion.");
    }
  }

  private static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
