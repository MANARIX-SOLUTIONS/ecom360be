package com.ecom360.shared.domain.exception;

public class ResourceAlreadyExistsException extends DomainException {

  public ResourceAlreadyExistsException(String message) {
    super(message);
  }

  public ResourceAlreadyExistsException(String resource, String identifier) {
    super(String.format("%s already exists: %s", resource, identifier));
  }
}
