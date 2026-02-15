package com.ecom360.supplier.application.dto;
import java.util.UUID;
public record PurchaseOrderLineResponse(UUID id, UUID productId, Integer quantity, Integer unitCost, Integer lineTotal) {}
