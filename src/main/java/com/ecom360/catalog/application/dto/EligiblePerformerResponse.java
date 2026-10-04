package com.ecom360.catalog.application.dto;

import java.util.UUID;

public record EligiblePerformerResponse(UUID businessUserId, String fullName) {}
