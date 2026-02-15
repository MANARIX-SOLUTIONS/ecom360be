package com.ecom360.sales.application.dto;
import java.util.UUID;
public record SaleLineResponse(UUID id, UUID productId, String productName, Integer quantity, Integer unitPrice, Integer lineTotal) {}
