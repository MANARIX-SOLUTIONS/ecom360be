package com.ecom360.identity.application.dto;

import jakarta.validation.constraints.Size;

public record DemoRejectRequest(@Size(max = 2000) String reason) {}
