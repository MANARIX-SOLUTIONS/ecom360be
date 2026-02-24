package com.ecom360.notification.application.dto;

import java.util.Map;

public record NotificationPreferencesUpdateRequest(Map<String, Boolean> preferences) {}
