package com.ecom360.integration.commerce.infrastructure.web;

import com.ecom360.integration.commerce.application.dto.WebhookIngestionResponse;
import com.ecom360.integration.commerce.application.service.CommerceOrderIngestionService;
import com.ecom360.integration.commerce.domain.model.CommerceOrderIngestionStatus;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.API_BASE + "/public/commerce/webhooks")
@Tag(
    name = "Public commerce",
    description =
        "Webhooks entrants (sans JWT) : jeton d’URL + en-tête HMAC-SHA256 du corps (secret à la création)")
public class PublicCommerceOrderWebhookController {

  private final CommerceOrderIngestionService commerceOrderIngestionService;

  public PublicCommerceOrderWebhookController(CommerceOrderIngestionService commerceOrderIngestionService) {
    this.commerceOrderIngestionService = commerceOrderIngestionService;
  }

  @PostMapping(value = "/incoming/{incomingToken}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Recevoir une commande canonique (site custom, connecteur, etc.)",
      description =
          "Corps JSON : modèle canonique (sourceType, externalOrderId, currency, lines, ...). "
              + "Signature : HMAC-SHA256 (UTF-8) du corps brut, hex minuscule dans "
              + ApiConstants.X_COMMERCE_SIGNATURE
              + ". Réponse 202 si accepté, 200 si idempotence (déjà traité).")
  public ResponseEntity<WebhookIngestionResponse> receive(
      @PathVariable String incomingToken,
      @Parameter(description = "HMAC-SHA256 du corps en hex (ou sha256=<hex>)", required = true)
          @RequestHeader(value = ApiConstants.X_COMMERCE_SIGNATURE, required = false)
          String signature,
      @RequestBody String rawBody) {
    return toHttpResponse(
        commerceOrderIngestionService.ingest(incomingToken, rawBody, signature));
  }

  @PostMapping(value = "/incoming/{incomingToken}/woocommerce", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Recevoir une commande WooCommerce (REST / webhook)",
      description =
          "Corps JSON : objet commande WooCommerce (tel que renvoyé par l’API REST v3 ou le webhook « Order »). "
              + "Connexion avec sourceType WOOCOMMERCE. Même signature HMAC sur ce corps brut. "
              + "Mapping interne vers le modèle canonique puis même pipeline que l’endpoint canonique.")
  public ResponseEntity<WebhookIngestionResponse> receiveWooCommerce(
      @PathVariable String incomingToken,
      @Parameter(description = "HMAC-SHA256 du corps WooCommerce brut en hex", required = true)
          @RequestHeader(value = ApiConstants.X_COMMERCE_SIGNATURE, required = false)
          String signature,
      @RequestBody String rawBody) {
    return toHttpResponse(
        commerceOrderIngestionService.ingestWooCommerce(incomingToken, rawBody, signature));
  }

  private static ResponseEntity<WebhookIngestionResponse> toHttpResponse(
      WebhookIngestionResponse r) {
    if (CommerceOrderIngestionStatus.DUPLICATE_SKIPPED.name().equals(r.status())) {
      return ResponseEntity.ok(r);
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(r);
  }
}
