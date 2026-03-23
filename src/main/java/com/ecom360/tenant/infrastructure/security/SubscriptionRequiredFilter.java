package com.ecom360.tenant.infrastructure.security;

import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.infrastructure.web.ApiConstants;
import com.ecom360.tenant.domain.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * When trial/subscription has expired, block access to tenant resources except subscription
 * endpoints (plans, change) so the user can subscribe.
 */
@Component
public class SubscriptionRequiredFilter extends OncePerRequestFilter {

  private static final String SUBSCRIPTION_PATH = ApiConstants.API_BASE + "/subscription";

  private final SubscriptionRepository subscriptionRepository;
  private final ObjectMapper objectMapper;

  public SubscriptionRequiredFilter(
      SubscriptionRepository subscriptionRepository, ObjectMapper objectMapper) {
    this.subscriptionRepository = subscriptionRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      filterChain.doFilter(request, response);
      return;
    }

    Object principal = auth.getPrincipal();
    if (!(principal instanceof UserPrincipal p)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!p.hasBusinessAccess() || p.businessId() == null) {
      filterChain.doFilter(request, response);
      return;
    }

    if (request.getRequestURI().startsWith(SUBSCRIPTION_PATH)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (request.getRequestURI().startsWith(ApiConstants.API_BASE + "/admin")) {
      filterChain.doFilter(request, response);
      return;
    }

    boolean hasActiveSubscription =
        subscriptionRepository
            .findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc(
                p.businessId(), com.ecom360.tenant.domain.model.SubscriptionStatus.ACCESS_GRANTING)
            .filter(
                sub ->
                    sub.getCurrentPeriodEnd() != null
                        && !sub.getCurrentPeriodEnd().isBefore(java.time.LocalDate.now()))
            .isPresent();

    if (hasActiveSubscription) {
      filterChain.doFilter(request, response);
      return;
    }

    response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            objectMapper.writeValueAsString(
                Map.of(
                    "code",
                    "SUBSCRIPTION_REQUIRED",
                    "message",
                    "Votre période d'essai est terminée. Veuillez souscrire à un plan pour continuer.")));
    return;
  }
}
