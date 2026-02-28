package com.ecom360.admin.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AdminCreateBusinessRequest(
    @NotBlank(message = "Business name is required") @Size(max = 255) String name,
    @NotBlank(message = "Business email is required") @Email @Size(max = 255) String email,
    @Size(max = 50) String phone,
    @Size(max = 1000) String address,
    /** Plan slug (e.g. pro, starter). If null or "trial", a trial subscription is created. */
    @Size(max = 50) String planSlug,
    /** If provided, this user is linked as propriétaire of the business. */
    UUID ownerUserId) {}
