package com.ecom360.shared.domain.exception;

/** Thrown when a business/domain rule is violated. */
public class BusinessRuleException extends DomainException {

  public BusinessRuleException(String message) {
    super(message);
  }
}
