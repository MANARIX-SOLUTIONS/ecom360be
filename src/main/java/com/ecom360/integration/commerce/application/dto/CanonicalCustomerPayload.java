package com.ecom360.integration.commerce.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CanonicalCustomerPayload(
    @Size(max = 300) String name,
    @Size(max = 320) String email,
    @Size(max = 64) String phone,
    @Size(max = 1000) String address) {}
