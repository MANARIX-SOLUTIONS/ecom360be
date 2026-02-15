package com.ecom360.identity.infrastructure.security;

import com.ecom360.shared.infrastructure.web.ApiConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String jwt = extractJwt(request);
      if (StringUtils.hasText(jwt) && "access".equals(jwtService.parseToken(jwt).type())) {
        JwtService.JwtClaims claims = jwtService.parseToken(jwt);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (claims.role() != null) {
          authorities.add(new SimpleGrantedAuthority("ROLE_" + claims.role().toUpperCase()));
        }
        if (claims.platformAdmin()) {
          authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }

        UserPrincipal principal =
            new UserPrincipal(
                claims.userId(),
                claims.email(),
                claims.businessId(),
                claims.role(),
                claims.platformAdmin());

        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    } catch (JwtAuthenticationException ignored) {
    }

    filterChain.doFilter(request, response);
  }

  private String extractJwt(HttpServletRequest request) {
    String bearer = request.getHeader(ApiConstants.AUTHORIZATION);
    if (StringUtils.hasText(bearer) && bearer.startsWith(ApiConstants.BEARER_PREFIX)) {
      return bearer.substring(ApiConstants.BEARER_PREFIX.length());
    }
    return null;
  }
}
