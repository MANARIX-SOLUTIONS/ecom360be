package com.ecom360.tenant.payment.infrastructure.bictorys;

public record BictorysStatusResult(String status, Integer amount) {

  public static boolean isSucceeded(String status) {
    if (status == null) {
      return false;
    }
    String s = status.toLowerCase();
    return "succeeded".equals(s) || "authorized".equals(s);
  }

  public static boolean isFailed(String status) {
    if (status == null) {
      return false;
    }
    String s = status.toLowerCase();
    return "failed".equals(s)
        || "cancelled".equals(s)
        || "canceled".equals(s)
        || "reversed".equals(s);
  }

  public boolean isSucceeded() {
    return isSucceeded(status);
  }

  public boolean isFailed() {
    return isFailed(status);
  }
}
