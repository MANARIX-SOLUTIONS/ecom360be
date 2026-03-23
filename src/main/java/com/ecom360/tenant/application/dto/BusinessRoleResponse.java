package com.ecom360.tenant.application.dto;

import java.util.List;
import java.util.UUID;

public record BusinessRoleResponse(
    UUID id, UUID businessId, String code, String name, boolean system, List<String> permissions) {}
