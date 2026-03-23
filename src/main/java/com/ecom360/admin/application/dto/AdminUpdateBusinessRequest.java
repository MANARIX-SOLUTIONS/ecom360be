package com.ecom360.admin.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record AdminUpdateBusinessRequest(
    @Size(max = 255) String name,
    @Email @Size(max = 255) String email,
    @Size(max = 50) String phone,
    @Size(max = 1000) String address) {}
