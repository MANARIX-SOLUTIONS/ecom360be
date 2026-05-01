package com.ecom360.integration.commerce.application.dto;

import java.util.UUID;

public record WebhookIngestionResponse(
    String status, UUID ingestionLogId, String externalOrderId, String message, UUID saleId) {}
