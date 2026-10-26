# HTTP errors and the request pipeline

How every `/api/v1` call is authenticated, rejected, and serialized. Verified from `RequestLoggingFilter`, `SecurityConfig`, `JwtAuthenticationFilter`, `SubscriptionRequiredFilter`, `GlobalExceptionHandler`, `JacksonConfig`, and the list controllers.

Tenant scope is the `businessId` claim on the JWT. `X-Business-Id` is declared in `ApiConstants` and has no reader.

## Pipeline

1. `RequestLoggingFilter` (`@Order(HIGHEST_PRECEDENCE)`) runs first.
2. `JwtAuthenticationFilter` runs before Spring Security's username/password filter.
3. `SubscriptionRequiredFilter` runs immediately after the JWT filter.
4. `SecurityConfig` then enforces public paths, `/api/v1/admin/**`, and authenticated access.
5. Controller and service exceptions reach `GlobalExceptionHandler`.

`SecurityConfig` is stateless, CSRF is disabled, and method security is enabled. The only `@PreAuthorize` methods are product create and product image upload (`PRODUCTS_CREATE` / `PRODUCTS_UPDATE`, or `ROLE_PLATFORM_ADMIN`). Other permission checks call `RolePermissionService` inside the service.

Public matchers (no JWT):

- `POST /api/v1/auth/login`, `/auth/demo-request`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`
- `/api/v1/public/**`
- `/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` (springdoc is disabled in the `prod` profile)
- `/actuator/health`, `/actuator/health/**`, `/actuator/info`
- `OPTIONS /**`

`/api/v1/admin/**` requires `ROLE_PLATFORM_ADMIN`. The JWT filter adds that authority when the `platformAdmin` claim is true, and also adds `ROLE_<role>` uppercased.

## Request id and client IP

Send `X-Request-Id` or omit it. A blank or missing value becomes 12 hex characters taken from a random UUID. The same value is stored in the logging MDC as `requestId`, returned on the response, and listed in CORS exposed headers (that header only).

Non-actuator requests are logged as `METHOD URI STATUS DURATION`. Dev logback prints `requestId`. The prod Logstash encoder includes the MDC key and also writes `logs/ecom360-api.log` (100MB files, 30 days, 3GB cap).

`RequestContext` keeps the id and client IP on the request thread. Client IP is the first `X-Forwarded-For` hop, otherwise `remoteAddr`. There is no trusted-proxy allowlist. `AuditLogService.logAsync` copies both values on the request thread before the `@Async` call, because the thread-local does not follow the async executor. `AsyncConfig` only logs uncaught async errors; it does not copy MDC.

## Three error bodies

Clients see different JSON depending on which layer rejects the call. `server.error.include-message` changes only responses produced by `sendError` (the Boot error page). ProblemDetail and the two filters below write their own bodies in every profile.

### ProblemDetail (controller advice)

`GlobalExceptionHandler` returns RFC 7807 `ProblemDetail`: `type` stays `about:blank`, plus `title`, `status`, `detail`, and a `timestamp` property. `instance` is left unset.

| Exception | HTTP | `title` | `detail` |
| --- | --- | --- | --- |
| `ResourceNotFoundException` | 404 | Resource Not Found | exception message |
| `NoResourceFoundException` | 404 | Not Found | `Resource not found` |
| `ResourceAlreadyExistsException` | 409 | Resource Already Exists | exception message |
| `BusinessRuleException` | 422 | Business Rule Violation | exception message |
| Domain `AccessDeniedException` | 403 | Forbidden | exception message |
| Spring `AccessDeniedException` (`@PreAuthorize`) | 403 | Access Denied | Spring's message |
| `UnauthorizedCommerceWebhookException` | 401 | Unauthorized | exception message |
| `DomainException` (no more specific handler) | 400 | Bad Request | exception message |
| `IllegalArgumentException` | 400 | Bad Request | exception message |
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 | Validation Error | `Validation failed` |
| Any other `Exception` | 500 | Internal Server Error | `An unexpected error occurred` |

`RolePermissionService.require` throws the domain 403 with `Accès refusé : permission <ENUM> requise pour votre rôle`. Platform admins pass `can()` without a permission row. A missing principal, a user with no business access, or a null `roleId` fails the check.

Bean-validation field errors are a map on `errors`. Repeated messages for one field are joined with `"; "`. `ConstraintViolationException` uses the property path as the key and keeps the later message when two violations share a path.

```json
{
  "type": "about:blank",
  "title": "Validation Error",
  "status": 400,
  "detail": "Validation failed",
  "timestamp": "2026-10-26T20:00:00Z",
  "errors": {
    "name": "must not be blank"
  }
}
```

The catch-all `Exception` handler logs `Unhandled exception` at ERROR and hides the cause. These failures therefore come back as HTTP 500 with the fixed detail above:

- Malformed JSON or a missing `@RequestBody` (`HttpMessageNotReadableException`)
- A path or query value that cannot be converted, including a bad UUID (`MethodArgumentTypeMismatchException`)
- A missing required query parameter
- An HTTP method the mapping does not declare
- A database constraint failure (`DataIntegrityViolationException` has no dedicated handler)

`page < 0` or a page `size < 1` is an `IllegalArgumentException` from `PageRequest` and stays HTTP 400.

`GlobalExceptionHandler.handleJwt` would return ProblemDetail 401, but no request path throws `JwtAuthenticationException` into the advice. The filter catches it first.

### Unauthenticated (security entry point)

`JwtAuthenticationEntryPoint` writes:

```json
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/v1/stores"
}
```

`message` is the `AuthenticationException` text, or `Authentication required` when that text is null. For an anonymous request the framework message is `Full authentication is required to access this resource`. The sample above is that case. The entry point does not rewrite it.

`JwtAuthenticationFilter` behavior that leads here:

- No `Authorization` header, or a value that does not start with `Bearer `, leaves the request anonymous.
- `JwtService.parseToken` throws `JwtAuthenticationException` with `Token expired` or `Invalid token`. The filter swallows it and does not log it.
- A token whose `type` claim is not `access` (refresh tokens) is ignored the same way.
- A secret shorter than 32 bytes fails startup: `JWT secret must be at least 256 bits (32 characters)`. The `prod` profile has no default for `JWT_SECRET`.

### Subscription block (filter)

When the principal is a `UserPrincipal` with a non-null `businessId`, the filter loads `findFirstByBusinessIdAndStatusInOrderByCreatedAtDesc` for statuses `trialing` and `active`. Access is allowed only when that row exists and `currentPeriodEnd` is on or after today. `past_due`, `expired`, `cancelled`, `paused`, and `incomplete` are outside the query. A null period end fails the check. A newer `trialing` or `active` row whose period has ended blocks the request even if an older granting row is still in range, because only the newest matching row is tested.

Skipped paths: `/api/v1/subscription`, `/api/v1/admin`, `/api/v1/public/`. A null `businessId` skips the check as well (including a platform admin who has no business on the token).

The filter writes HTTP 402, `Content-Type: application/json;charset=UTF-8`:

```json
{
  "code": "SUBSCRIPTION_REQUIRED",
  "message": "Votre période d'essai est terminée. Veuillez souscrire à un plan pour continuer."
}
```

### Admin role rejection

An authenticated caller without `ROLE_PLATFORM_ADMIN` who hits `/api/v1/admin/**` is rejected in the security filter chain. The default access-denied handler calls `sendError(403)`. That body is Boot's error JSON (`timestamp`, `status`, `error`, `path`). Base configuration includes `message` (`server.error.include-message: always`). The `prod` profile sets `include-message` and `include-binding-errors` to `never`. Dev adds stack traces only when `?trace=true`.

## Pagination

List endpoints that return `PageResponse` use `page` (default `0`) and `size` (default `20`, `ApiConstants.DEFAULT_PAGE_SIZE`). Size is clamped with `Math.min` to 100 (`ApiConstants.MAX_PAGE_SIZE`); a larger `size` is accepted and shortened. The wrapper fields are `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`, `hasNext`, `hasPrevious`. `size` in the body is the page request size after the cap, not the number of rows in `content`.

`GET /api/v1/delivery/deliveries` returns a JSON array, not `PageResponse`. The business list caps `size` at 100. The `courierId` list caps `size` at 50.

These collections are not paged: categories, couriers, commerce connections, API keys, outbound webhook registrations, and several admin sub-resources (members, roles, stores).

## JSON

`JacksonConfig` replaces Boot's `ObjectMapper`. MVC responses and `SubscriptionRequiredFilter` use that bean:

- `JavaTimeModule` is registered and timestamps are disabled, so dates are ISO-8601 strings.
- Unknown properties are ignored.
- A single JSON value is accepted where a collection is declared (`ACCEPT_SINGLE_VALUE_AS_ARRAY`).
- `spring.jackson.default-property-inclusion: non_null` and `spring.jackson.time-zone` are not applied to this bean. Null fields are serialized.

`JwtAuthenticationEntryPoint` builds a separate `ObjectMapper` for its 401 body.

Multipart limits in base `application.yml` are 8MB per file and 10MB per request.

## CORS

`app.cors.allowed-origins` and `allowed-methods` are split on commas. `allowed-headers` is passed as a single list entry. The default `*` works. A comma-separated header list in that property is one header name, not several.

`allowCredentials` is set when `allowed-origins` is any value other than the exact string `*`. The dev profile default is `http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173`. The prod profile requires `CORS_ALLOWED_ORIGINS` with no default. Preflight max age is 3600 seconds.

## Troubleshooting

| What you see | Where to look |
| --- | --- |
| 401 `error`/`message`/`path`, including a fresh access token | Filter swallowed `Token expired` or `Invalid token`, or the token `type` is `refresh`. The reason is not in the body. |
| 402 `code: SUBSCRIPTION_REQUIRED` | Status is not `trialing`/`active`, or `currentPeriodEnd` is null or before today. `/api/v1/subscription` still answers. |
| 403 ProblemDetail `Forbidden` | `RolePermissionService` or a service `AccessDeniedException`. `detail` names the permission. |
| 403 ProblemDetail `Access Denied` | `@PreAuthorize` on product create or image upload. |
| 403 Boot error JSON on `/admin/**` | Authenticated, missing `ROLE_PLATFORM_ADMIN`. |
| 422 `Business Rule Violation` | Plan limit, stock rule, or other `BusinessRuleException`. `detail` is the rule text. |
| 500 `An unexpected error occurred` | Catch-all. Read the `Unhandled exception` log line for the same `X-Request-Id`. Foreign-key deletes and bad JSON both land here. |
| Stale list after a write on another node | Caches are in-process Caffeine. This page does not change cache keys; see the lookup and store docs. |
