package com.ecom360.integration.commerce.application.service;

import com.ecom360.shared.domain.exception.UnauthorizedCommerceWebhookException;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Vérifie {@code X-Commerce-Signature} = HMAC-SHA256 (corps brut UTF-8, secret partagé), encodé en
 * hexadécimal (minuscules). Formes acceptées pour la signature : {@code <hex>} ou {@code sha256=<hex>}.
 */
@Component
public class CommerceWebhookHmacVerifier {

  public void verify(String hmacSecret, String rawBodyUtf8, String signatureHeader) {
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new UnauthorizedCommerceWebhookException(
          "En-tête "
              + ApiConstants.X_COMMERCE_SIGNATURE
              + " requis (HMAC-SHA256 du corps en hex).");
    }
    String normalized = normalizeSignature(signatureHeader);
    byte[] expected = hmacSha256(hmacSecret, rawBodyUtf8);
    byte[] provided;
    try {
      provided = HexFormat.of().parseHex(normalized);
    } catch (IllegalArgumentException e) {
      throw new UnauthorizedCommerceWebhookException("Signature HMAC invalide (hex attendu).");
    }
    if (!MessageDigest.isEqual(expected, provided)) {
      throw new UnauthorizedCommerceWebhookException("Signature HMAC invalide.");
    }
  }

  private static String normalizeSignature(String header) {
    String s = header.trim();
    int eq = s.indexOf('=');
    if (eq > 0 && eq < s.length() - 1) {
      String prefix = s.substring(0, eq).trim().toLowerCase();
      if ("sha256".equals(prefix) || "v1".equals(prefix)) {
        s = s.substring(eq + 1).trim();
      }
    }
    return s.toLowerCase();
  }

  private static byte[] hmacSha256(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256", e);
    }
  }
}
