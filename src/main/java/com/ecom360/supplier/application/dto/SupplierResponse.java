package com.ecom360.supplier.application.dto;
import java.time.Instant; import java.util.UUID;
public record SupplierResponse(UUID id, UUID businessId, String name, String phone, String email, String zone, String address, Integer balance, Boolean isActive, Instant createdAt, Instant updatedAt) {}
