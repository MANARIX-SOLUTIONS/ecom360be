package com.ecom360.catalog.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReplaceProductPerformersRequest(@NotNull List<@NotNull UUID> businessUserIds) {}
