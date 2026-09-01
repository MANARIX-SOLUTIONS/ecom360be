package com.ecom360.shared.domain.exception;

/** Thrown when a sale or adjustment would drive stock below zero. Mapped to HTTP 409. */
public class InsufficientStockException extends DomainException {

  public static final String CODE = "INSUFFICIENT_STOCK";

  public InsufficientStockException(String message) {
    super(message);
  }

  public static InsufficientStockException forSale(int available, int requested) {
    return new InsufficientStockException(
        "Stock insuffisant pour ce produit (disponible: "
            + available
            + ", demandé: "
            + requested
            + ")");
  }
}
