package com.ecom360.client.domain;

import com.ecom360.client.domain.model.Client;
import com.ecom360.shared.domain.exception.BusinessRuleException;
import java.util.Locale;
import java.util.Set;

/**
 * Règles du crédit client : solde positif = le client doit, négatif =
 * trop-perçu.
 * Le client comptoir (vente anonyme) ne peut ni acheter à crédit ni rembourser.
 */
public final class ClientCreditPolicy {

  public static final String CREDIT_METHOD = "credit";
  public static final String PLAN_REQUIRED = "Crédits clients non inclus dans votre plan. Passez à un plan supérieur.";
  public static final String NAMED_CLIENT_REQUIRED = "Pour le crédit, sélectionnez un client nominatif.";
  public static final String NOTHING_OWED = "Ce client n'a rien à régler.";
  public static final String EXCEEDS_BALANCE = "Le paiement ne peut pas dépasser le solde dû.";
  public static final String WALK_IN_NO_PAYMENT = "Le client comptoir n'a pas de crédit nominatif.";
  public static final String IMPORT_CREDIT_FORBIDDEN = "Les ventes à crédit ne sont pas disponibles pour les importations commerce.";

  private static final Set<String> WALK_IN_ALIASES = Set.of("client comptoir", "walk-in", "walk in", "client anonyme");

  private ClientCreditPolicy() {
  }

  public static boolean isCreditPayment(String paymentMethod) {
    return CREDIT_METHOD.equals(paymentMethod);
  }

  public static boolean isWalkInName(String name) {
    if (name == null) {
      return false;
    }
    return WALK_IN_ALIASES.contains(name.trim().toLowerCase(Locale.ROOT));
  }

  public static void requireFeatureEnabled(Boolean featureClientCredits) {
    if (!Boolean.TRUE.equals(featureClientCredits)) {
      throw new BusinessRuleException(PLAN_REQUIRED);
    }
  }

  public static void requireNamedClientForCredit(String paymentMethod, Client client) {
    if (!isCreditPayment(paymentMethod)) {
      return;
    }
    if (client == null || client.isWalkIn()) {
      throw new BusinessRuleException(NAMED_CLIENT_REQUIRED);
    }
  }

  public static void requireRepayment(Client client, int amount) {
    if (client == null || client.isWalkIn()) {
      throw new BusinessRuleException(WALK_IN_NO_PAYMENT);
    }
    int balance = client.getCreditBalance() == null ? 0 : client.getCreditBalance();
    if (balance <= 0) {
      throw new BusinessRuleException(NOTHING_OWED);
    }
    if (amount > balance) {
      throw new BusinessRuleException(EXCEEDS_BALANCE);
    }
  }
}
