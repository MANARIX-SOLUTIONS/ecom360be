package com.ecom360.integration.application.dto;

public record WebhookTestResponse(
        boolean success, int httpStatus, String message, long durationMs) {
}
