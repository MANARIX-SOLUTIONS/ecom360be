package com.ecom360.catalog.application.dto;

import java.util.UUID;

public record ProductPerformerResponse(UUID businessUserId, String fullName, boolean active) {}
