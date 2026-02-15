package com.ecom360.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Convenient base record for domain events.
 */
public abstract class BaseDomainEvent implements DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredOn = Instant.now();

    @Override
    public UUID eventId() { return eventId; }

    @Override
    public Instant occurredOn() { return occurredOn; }
}
