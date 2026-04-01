package com.ecom360.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoRequestSubmitRequest(
    @NotBlank @Size(min = 2, max = 255) String fullName,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 50) String phone,
    @NotBlank @Size(min = 2, max = 255) String businessName,
    @Size(max = 2000) String message,
    @Size(max = 128) String jobTitle,
    @Size(max = 128) String city,
    @Size(max = 128) String sector) {}
