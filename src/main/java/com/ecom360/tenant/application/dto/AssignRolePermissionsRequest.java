package com.ecom360.tenant.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignRolePermissionsRequest(@NotNull List<String> permissionCodes) {}
