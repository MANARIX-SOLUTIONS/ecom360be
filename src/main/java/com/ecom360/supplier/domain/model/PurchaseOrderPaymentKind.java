package com.ecom360.supplier.domain.model;

/** Nature d'un versement : acompte à la réception, ou règlement ultérieur. */
public final class PurchaseOrderPaymentKind {

  public static final String DEPOSIT = "deposit";
  public static final String INSTALLMENT = "installment";

  private PurchaseOrderPaymentKind() {}
}
