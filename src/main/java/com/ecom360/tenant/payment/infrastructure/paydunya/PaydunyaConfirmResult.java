package com.ecom360.tenant.payment.infrastructure.paydunya;

public record PaydunyaConfirmResult(
    String status, String hash, String failReason, Integer totalAmount) {

  public boolean isCompleted() {
    return status != null && "completed".equalsIgnoreCase(status);
  }

  public boolean isFailedOrCancelled() {
    if (status == null) {
      return false;
    }
    String s = status.toLowerCase();
    return "failed".equals(s) || "cancelled".equals(s) || "canceled".equals(s);
  }
}
