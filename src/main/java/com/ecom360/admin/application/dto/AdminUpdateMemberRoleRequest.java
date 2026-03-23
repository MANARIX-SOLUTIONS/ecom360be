package com.ecom360.admin.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUpdateMemberRoleRequest(@NotBlank @Size(max = 64) String roleCode) {}
