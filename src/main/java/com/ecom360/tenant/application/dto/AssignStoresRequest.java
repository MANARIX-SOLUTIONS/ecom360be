package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record AssignStoresRequest(@NotNull List<UUID> storeIds) {}
