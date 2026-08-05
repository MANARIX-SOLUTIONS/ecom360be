package com.ecom360.tenant.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateSubscriptionCheckoutRequest(
        @NotBlank String planSlug,
        @NotBlank @Pattern(regexp = "monthly|yearly", flags = Pattern.Flag.CASE_INSENSITIVE, message = "billingCycle must be monthly or yearly") String billingCycle,
        @NotBlank @Pattern(regexp = "wave|orange_money", flags = Pattern.Flag.CASE_INSENSITIVE, message = "channel must be wave or orange_money") String channel) {
}
