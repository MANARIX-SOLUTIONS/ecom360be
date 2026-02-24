package com.ecom360.shared.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Thread-local context for the current HTTP request. Holds client IP and request ID for use in
 * audit logs and correlation across async boundaries.
 */
public final class RequestContext {

  private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

  public record Context(String requestId, String clientIp) {}

  private RequestContext() {}

  public static void set(String requestId, String clientIp) {
    HOLDER.set(new Context(requestId, clientIp));
  }

  public static Context get() {
    return HOLDER.get();
  }

  public static String getRequestId() {
    Context ctx = HOLDER.get();
    return ctx != null ? ctx.requestId() : null;
  }

  public static String getClientIp() {
    Context ctx = HOLDER.get();
    return ctx != null ? ctx.clientIp() : null;
  }

  public static void clear() {
    HOLDER.remove();
  }

  /**
   * Extracts client IP from request, respecting X-Forwarded-For when behind a proxy.
   */
  public static String extractClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      // First IP is the original client when behind multiple proxies
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
