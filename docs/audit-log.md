# Audit log

Source-backed guide for the `audit_log` table, async writers, tenant list, and
platform-admin cross-tenant list.

Verified against `AuditLog` / `AuditLogService` / `AuditLogRepository`,
`AuditLogController`, `AdminAuditController`, `RequestLoggingFilter`,
`RequestContext`, `AsyncConfig`, `V1__initial_schema.sql`,
`V47__audit_log_request_id.sql`, and every `auditLogService.logAsync` caller.

## Intent

- Record security- and domain-relevant mutations as append-only rows.
- Correlate HTTP access logs (`X-Request-Id` / MDC `requestId`) with audit
  rows via `request_id`.
- Expose a tenant-scoped list and a platform-admin list. There is **no**
  `AUDIT_*` permission in `Permission`.

## Schema

Table `audit_log` (`V1`, `request_id` in `V47`):

| Column | Notes |
|--------|--------|
| `id` | UUID PK |
| `business_id` | Nullable FK → `business` (null for some demo-request events) |
| `user_id` | Nullable FK → `users` |
| `action` | `VARCHAR(50)`, required |
| `entity_type` | `VARCHAR(100)`, required |
| `entity_id` | Optional UUID |
| `changes` | JSONB map |
| `ip_address` | `VARCHAR(50)` from `RequestContext` (`X-Forwarded-For` first hop, else `remoteAddr`) |
| `request_id` | `VARCHAR(50)`; 12-char hex if the client omitted `X-Request-Id` |
| `created_at` | Set in `@PrePersist` (`Instant.now()`), not `BaseEntity` |

Index: `(business_id, created_at DESC)` plus partial index on `request_id`.

Seed `V25` inserts a lowercase action `create` / entity `sale`. Application
writers use **uppercase** actions (`CREATE`, `LOGIN`, …). Treat casing as
opaque when filtering.

## Write path

`AuditLogService.log` is synchronous. **No caller uses it today.** All writers
call `logAsync(...)`.

The convenience overload copies `RequestContext` IP + request id on the
**request thread**, then `@Async` persists. ThreadLocals do not follow the
async worker; passing IP/requestId explicitly is required (the convenience
method does that). Persist failures are logged at WARN and swallowed — the
HTTP request still succeeds.

`RequestLoggingFilter` (highest precedence) sets `RequestContext` and echoes
`X-Request-Id` on the response. It clears the ThreadLocal in `finally`.

`AsyncConfig` (`@EnableAsync`) logs uncaught async errors; `logAsync` already
catches persist exceptions itself.

## Who writes what

Only these services call `auditLogService` on `main`:

| Caller | Action | Entity type | `business_id` | Typical `changes` |
|--------|--------|-------------|----------------|-------------------|
| `AuthService.login` | `LOGIN` | `Auth` | first active membership | `email` |
| `AuthService.provisionTenantAfterDemoApproval` | `REGISTER` | `Auth` | new business | `email`, `businessName`, `source=demo_request` |
| `AuthService.changePassword` | `PASSWORD_CHANGE` | `Auth` | JWT business (skipped if null) | empty map |
| `DemoRequestService.submit` | `DEMO_REQUEST` | `DemoRequest` | **null** | `email`, `businessName` |
| `DemoRequestService.approve` | `DEMO_REQUEST_APPROVED` | `DemoRequest` | provisioned business | `email`, `businessName` |
| `DemoRequestService.reject` | `DEMO_REQUEST_REJECTED` | `DemoRequest` | **null** | `email`, `reason` |
| `ProductService.create` | `CREATE` | `Product` | tenant | `name`, `sku` |
| `ProductService.update` | `UPDATE` | `Product` | tenant | `name` |
| `ProductService.uploadImage` | `UPDATE` | `Product` | tenant | `name`, `action=image_upload` |
| `ProductService.delete` | `DELETE` | `Product` | tenant | `name` (soft-delete follows) |
| `AdminBusinessMemberService.updateMemberRole` | `UPDATE` | `BusinessUser` | path business | previous/new role, member email, `source=platform_admin` |
| `AdminBusinessRoleService.updateRolePermissions` | `UPDATE` | `BusinessRole` | path business | role code/name, permission list, `source=platform_admin` |

Sales, expenses, stock, suppliers, subscriptions, and tenant member invites
are **not** audited. Do not assume a complete change history.

Forgot/reset password does not write an audit row. Refresh token does not.

## Tenant list

`GET /api/v1/audit-logs`

Requires a JWT with business context (`hasBusinessAccess()`). There is no
`@PreAuthorize` and no `Permission` check — any member of the business can
list. Platform admins with a `businessId` on the token can also call this;
results stay scoped to that business.

Query params: `entityType`, `userId`, `page` (default 0), `size` (default 20,
capped at 100).

Filter precedence in `AuditLogService.list`:

1. If `entityType` is present → `findByBusinessIdAndEntityType…` (**`userId` ignored**).
2. Else if `userId` is present → `findByBusinessIdAndUserId…`.
3. Else all rows for the business.

Order: `createdAt` descending.

```bash
curl "http://localhost:8080/api/v1/audit-logs?entityType=Product&page=0&size=20" \
  --header 'Authorization: Bearer <tenant-token>'
```

Missing business context → 403 *« Business context required »*.

## Platform-admin list

`GET /api/v1/admin/audit-logs`

Requires `ROLE_PLATFORM_ADMIN` (HTTP security) **and**
`UserPrincipal.isPlatformAdmin()` in the service.

Query params: `businessId`, `entityType`, `userId`, `page`, `size` (cap 100).

Filter precedence in `listForAdmin`:

1. If `businessId` is set, the same entityType-then-userId rules as the tenant
   list apply **inside that business**.
2. If `businessId` is omitted → `findAllByOrderByCreatedAtDesc`. **`entityType`
   and `userId` are ignored.** To filter by user or entity across the platform,
   you must also pass `businessId` (or filter client-side).

Rows with `business_id` null (demo submit/reject) only appear on the unfiltered
admin list, not on a `businessId=` query.

## Response

`AuditLogResponse`: `id`, `businessId`, `userId`, `action`, `entityType`,
`entityId`, `changes`, `ipAddress`, `requestId`, `createdAt`.

Correlate with application logs using the same `requestId` (MDC + response
header `X-Request-Id`).

## Constraints and pitfalls

- Writes are best-effort async: a 2xx HTTP response does not guarantee the
  audit row exists yet (or at all if persist failed).
- `ip_address` / `request_id` columns are 50 characters. IPv6 or a client
  `X-Request-Id` longer than 50 can fail the insert (caught, WARN).
- Tenant list is not an owner-only API. Hide it in the UI via nav rules if
  needed; the backend will still serve it to any authenticated member.
- Combining `entityType` and `userId` does not AND them; entity type wins.
- Admin global list cannot filter by entity/user without `businessId`.
- Seed vs runtime action casing (`create` vs `CREATE`) will miss in exact
  `entityType`/`action` filters if you mix them.

## Codepaths

- `audit/domain/model/AuditLog`
- `audit/application/service/AuditLogService`
- `audit/infrastructure/web/AuditLogController`
- `admin/infrastructure/web/AdminAuditController`
- `shared/infrastructure/web/RequestLoggingFilter`, `RequestContext`
- `shared/infrastructure/config/AsyncConfig`
- Writers: `AuthService`, `DemoRequestService`, `ProductService`,
  `AdminBusinessMemberService`, `AdminBusinessRoleService`
