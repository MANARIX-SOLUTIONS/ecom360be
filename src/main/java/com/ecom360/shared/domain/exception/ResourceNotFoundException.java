package com.ecom360.shared.domain.exception;

import java.util.UUID;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, UUID id) {
        super(String.format("%s not found with id: %s", resource, id));
    }

    public ResourceNotFoundException(String resource, String identifier) {
        super(String.format("%s not found: %s", resource, identifier));
    }
}
