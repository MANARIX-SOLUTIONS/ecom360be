package com.ecom360.admin.application.dto;

import java.util.UUID;

/** Plan item for admin dropdowns (create business, assign plan). */
public record AdminPlanItem(UUID id, String slug, String name, int priceMonthly, int priceYearly) {}
