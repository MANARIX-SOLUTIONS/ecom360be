package com.ecom360.supplier.domain.model;

/** Statut de règlement d'un bon de commande : soldé, acompte, ou rien versé. */
public final class PurchaseOrderPaymentStatus {

  public static final String PAID = "paid";
  public static final String PARTIAL = "partial";
  public static final String UNPAID = "unpaid";

  private PurchaseOrderPaymentStatus() {}

  public static boolean isValid(String value) {
    return PAID.equals(value) || PARTIAL.equals(value) || UNPAID.equals(value);
  }
}
