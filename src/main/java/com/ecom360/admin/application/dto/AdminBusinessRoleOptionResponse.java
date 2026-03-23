package com.ecom360.admin.application.dto;

import java.util.UUID;

public record AdminBusinessRoleOptionResponse(UUID id, String code, String name, boolean system) {}
