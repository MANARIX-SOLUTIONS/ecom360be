package com.ecom360.admin.application.dto;

public record AdminMaintenanceNotificationResult(
    int targetedBusinesses, int mailedBusinesses, int inAppRecipients) {}
