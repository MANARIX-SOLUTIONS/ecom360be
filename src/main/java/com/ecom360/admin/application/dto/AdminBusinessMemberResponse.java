package com.ecom360.admin.application.dto;

import java.util.UUID;

public record AdminBusinessMemberResponse(
    UUID id,
    UUID userId,
    String fullName,
    String email,
    String roleCode,
    String roleName,
    boolean active) {}
