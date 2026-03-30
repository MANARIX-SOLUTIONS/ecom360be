package com.ecom360.integration.commerce.woocommerce;

import com.ecom360.integration.commerce.application.dto.CanonicalCustomerPayload;
import com.ecom360.integration.commerce.application.dto.CanonicalOrderLinePayload;
import com.ecom360.integration.commerce.application.dto.CanonicalOrderPayload;
import com.ecom360.integration.commerce.application.dto.CanonicalShippingPayload;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Transforme un document JSON de commande WooCommerce (REST API v3 / webhook) vers le modèle
 * canonique ecom360.
 */
@Component
public class WooCommerceOrderMapper {

  public static final String SOURCE_TYPE_WOOCOMMERCE = "WOOCOMMERCE";

  private final ObjectMapper objectMapper;

  public WooCommerceOrderMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * @param rawWoocommerceJson corps JSON brut (objet commande à la racine, ou enveloppe {@code
   *     "order":{...}})
   */
  public CanonicalOrderPayload toCanonical(String rawWoocommerceJson)
      throws JsonProcessingException {
    JsonNode root = objectMapper.readTree(rawWoocommerceJson);
    JsonNode order = root.hasNonNull("order") ? root.get("order") : root;

    if (!order.hasNonNull("id")) {
      throw new BusinessRuleException("Payload WooCommerce : champ « id » de commande manquant.");
    }

    String externalOrderId = order.get("id").asText();
    Instant externalUpdatedAt = null;
    if (order.hasNonNull("date_modified_gmt")) {
      externalUpdatedAt = parseWcInstant(order.get("date_modified_gmt").asText());
    } else if (order.hasNonNull("date_created_gmt")) {
      externalUpdatedAt = parseWcInstant(order.get("date_created_gmt").asText());
    }

    String currency = order.hasNonNull("currency") ? order.get("currency").asText("XOF") : "XOF";
    String paymentStatus =
        order.hasNonNull("status") ? order.get("status").asText("unknown") : "unknown";

    List<CanonicalOrderLinePayload> lines = mapLineItems(order);
    if (lines.isEmpty()) {
      throw new BusinessRuleException(
          "Commande WooCommerce sans ligne produit exploitable (line_items vides ou seulement frais).");
    }

    CanonicalCustomerPayload customer = mapBilling(order.get("billing"));
    CanonicalShippingPayload shipping = mapShipping(order);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("wc_order_id", order.get("id").asLong());
    if (order.hasNonNull("number")) {
      metadata.put("wc_order_number", order.get("number").asText());
    }
    if (order.hasNonNull("order_key")) {
      metadata.put("wc_order_key", order.get("order_key").asText());
    }

    return new CanonicalOrderPayload(
        SOURCE_TYPE_WOOCOMMERCE,
        externalOrderId,
        externalUpdatedAt,
        currency,
        lines,
        customer,
        paymentStatus,
        shipping,
        metadata);
  }

  private static List<CanonicalOrderLinePayload> mapLineItems(JsonNode order) {
    JsonNode items = order.get("line_items");
    if (items == null || !items.isArray()) {
      return List.of();
    }
    List<CanonicalOrderLinePayload> lines = new ArrayList<>();
    for (JsonNode item : items) {
      if (item == null || item.isNull()) continue;
      String sku = textOrEmpty(item, "sku");
      int productId = item.hasNonNull("product_id") ? item.get("product_id").asInt(0) : 0;
      if (productId == 0 && sku.isBlank()) {
        continue;
      }
      int variationId = item.hasNonNull("variation_id") ? item.get("variation_id").asInt(0) : 0;
      int lineId = item.hasNonNull("id") ? item.get("id").asInt(0) : 0;
      if (sku.isBlank()) {
        if (productId > 0) {
          sku =
              variationId > 0
                  ? "wc-" + productId + "-v-" + variationId
                  : "wc-" + productId;
        } else if (lineId > 0) {
          sku = "wc-line-" + lineId;
        } else {
          throw new BusinessRuleException(
              "Ligne WooCommerce sans SKU ni product_id exploitable.");
        }
      }
      String label = item.hasNonNull("name") ? item.get("name").asText("Article") : "Article";
      int qty = item.hasNonNull("quantity") ? item.get("quantity").asInt(1) : 1;
      if (qty < 1) qty = 1;
      String priceStr = item.hasNonNull("price") ? item.get("price").asText("0") : "0";
      int unitMinor = parseMoneyMinorUnit(priceStr);
      lines.add(new CanonicalOrderLinePayload(null, sku, label, qty, unitMinor));
    }
    return lines;
  }

  private static CanonicalCustomerPayload mapBilling(JsonNode billing) {
    if (billing == null || billing.isNull()) {
      return null;
    }
    String fn = textOrEmpty(billing, "first_name");
    String ln = textOrEmpty(billing, "last_name");
    String name = (fn + " " + ln).trim();
    if (name.isEmpty()) {
      name = null;
    }
    String email = textOrNull(billing, "email");
    String phone = textOrNull(billing, "phone");
    StringBuilder addr = new StringBuilder();
    appendPart(addr, textOrNull(billing, "address_1"));
    appendPart(addr, textOrNull(billing, "address_2"));
    appendPart(addr, textOrNull(billing, "postcode"));
    appendPart(addr, textOrNull(billing, "city"));
    appendPart(addr, textOrNull(billing, "country"));
    String address = addr.length() > 0 ? addr.toString() : null;
    return new CanonicalCustomerPayload(name, email, phone, address);
  }

  private static void appendPart(StringBuilder sb, String part) {
    if (part == null || part.isBlank()) return;
    if (sb.length() > 0) sb.append(", ");
    sb.append(part.trim());
  }

  private static CanonicalShippingPayload mapShipping(JsonNode order) {
    String total = order.hasNonNull("shipping_total") ? order.get("shipping_total").asText("0") : "0";
    int amount = parseMoneyMinorUnit(total);
    String method = null;
    JsonNode sl = order.get("shipping_lines");
    if (sl != null && sl.isArray() && sl.size() > 0) {
      JsonNode first = sl.get(0);
      if (first != null && first.hasNonNull("method_title")) {
        method = first.get("method_title").asText();
      }
    }
    if (amount == 0 && (method == null || method.isBlank())) {
      return null;
    }
    return new CanonicalShippingPayload(amount, method);
  }

  private static String textOrNull(JsonNode n, String field) {
    if (n == null || !n.hasNonNull(field)) return null;
    String v = n.get(field).asText();
    return v != null && !v.isBlank() ? v.trim() : null;
  }

  private static String textOrEmpty(JsonNode n, String field) {
    String v = textOrNull(n, field);
    return v != null ? v : "";
  }

  private static int parseMoneyMinorUnit(String priceStr) {
    if (priceStr == null || priceStr.isBlank()) return 0;
    try {
      BigDecimal bd = new BigDecimal(priceStr.trim());
      return bd.setScale(0, RoundingMode.HALF_UP).intValueExact();
    } catch (Exception e) {
      throw new BusinessRuleException("Montant WooCommerce invalide : « " + priceStr + " ».");
    }
  }

  private static Instant parseWcInstant(String s) {
    if (s == null || s.isBlank()) return null;
    String t = s.trim();
    try {
      if (t.endsWith("Z")) {
        return Instant.parse(t);
      }
      return Instant.parse(t.replace(" ", "T") + "Z");
    } catch (Exception e) {
      return null;
    }
  }
}
