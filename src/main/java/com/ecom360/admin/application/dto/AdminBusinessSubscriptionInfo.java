package com.ecom360.admin.application.dto;

import java.time.LocalDate;

/** Détails du dernier abonnement connu pour une entreprise (backoffice). */
public record AdminBusinessSubscriptionInfo(
    String planSlug,
    String planName,
    String billingCycle,
    String status,
    LocalDate currentPeriodStart,
    LocalDate currentPeriodEnd,
    long daysRemaining,
    boolean cancelAtPeriodEnd,
    boolean trialing) {}
