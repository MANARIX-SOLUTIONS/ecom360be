package com.ecom360.identity.application.dto;

import java.util.UUID;

public record UserProfileResponse(UUID id, String fullName, String email, String phone) {}
