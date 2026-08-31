# Platform admin APIs

Source-backed guide for the backoffice under `/api/v1/admin/**`. Tenant role
permissions do not apply here; access is the Spring Security role
`ROLE_PLATFORM_ADMIN`.

Verified against `SecurityConfig`, `JwtAuthenticationFilter`, `AuthService`,
`SubscriptionRequiredFilter`, `Admin*Controller` / `Admin*Service`,
`DemoRequestService`, `SubscriptionUsageService`, `CachedLookups`,
`V31__seed_platform_admin.sql`, and `users.is_platform_admin`.

## Intent

- Operate the SaaS from a platform backoffice: tenants, plans, members, stores,
  users, demo requests, and [cross-tenant audit](audit-log.md).
- Bypass tenant subscription blocking and tenant RBAC for these routes.
- Keep mutations aligned with plan limits (stores, users) where the services
  re-use `SubscriptionService` / plan rows.

## Who is a platform admin

A user is a platform admin when `users.is_platform_admin = true` (JWT claim
`platformAdmin`). `UserPrincipal.isPlatformAdmin()` also treats JWT `role`
`PLATFORM_ADMIN` as admin.

Login is still `POST /api/v1/auth/login`. `AuthService.buildAuthResponse` then:

- sets token `role` to `PLATFORM_ADMIN` (not the tenant role code);
- sets `roleId` to `null` (no tenant permission authorities on the token);
- keeps `businessId` from the first **active + accepted** `business_user` row.

Login **requires** that membership. A platform-admin flag alone is not enough:
*« No active business membership found »*.

Local seed: `V31__seed_platform_admin.sql` sets `is_platform_admin = true` for
`demo@ecom360.local` (already a tenant member from `V5`).

`JwtAuthenticationFilter` adds `ROLE_PLATFORM_ADMIN` when the claim is true.
`SecurityConfig` requires that role for every `/api/v1/admin/**` path (403
otherwise). Several services also call `requirePlatformAdmin` as a second gate.

`RolePermissionService.can()` returns true for every tenant `Permission` when
`isPlatformAdmin()` — service-level `require()` on tenant APIs therefore passes.
The only controller-level `@PreAuthorize` today is on product create/update,
which explicitly allows `hasRole('PLATFORM_ADMIN')`.

## Subscription gate

`SubscriptionRequiredFilter` skips URIs starting with `/api/v1/admin`. Expired
tenant trials do not block backoffice calls. The admin JWT may still carry a
`businessId`; that membership is not used to authorize admin routes.

## Public surface

Base path: `/api/v1/admin`. JWT with `ROLE_PLATFORM_ADMIN` required. Page size
is capped at `ApiConstants.MAX_PAGE_SIZE` (100) except demo-request list, which
caps at 100 in the controller.

### Stats

| Method | Path | Service |
|--------|------|---------|
| `GET` | `/admin/stats` | `AdminStatsService.getStats` |

Response (`AdminStatsResponse`): `businessesCount`, `usersCount`, `storesCount`,
`monthlyRevenue`, `planDistribution`, `topBusinesses`.

- Counts are table-wide (`count()` on business / users / store).
- `monthlyRevenue` sums **completed** sales (`Sale.status = 'completed'`) from
  the 1st of the current month 00:00 **UTC** through tomorrow 00:00 UTC
  (`BETWEEN` — both ends inclusive). Amounts are integers (typically XOF).
- `planDistribution` walks **all** subscriptions, keeps those where
  `Subscription.isActive()` is true (status in `trialing` **and** `active`),
  groups by **plan name**, and computes `pct` against **total businesses** (not
  against the active-sub count). Businesses with no granting subscription do
  not appear as a slice.
- `topBusinesses`: top 5 by that monthly revenue; if none, the 5 most recently
  created businesses. Owner is the earliest `PROPRIETAIRE` membership.

### Businesses

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/admin/businesses` | Paged list |
| `GET` | `/admin/businesses/plans` | Active plans for dropdowns |
| `GET` | `/admin/businesses/{id}` | Detail |
| `GET` | `/admin/businesses/{id}/subscription/usage` | Usage vs plan limits |
| `POST` | `/admin/businesses` | Create tenant |
| `PATCH` | `/admin/businesses/{id}` | Update profile fields |
| `PATCH` | `/admin/businesses/{id}/plan` | Replace current granting sub |
| `POST` | `/admin/businesses/{id}/subscription/renew` | Add a billing period |
| `PATCH` | `/admin/businesses/{id}/status` | `active` or `suspended` |

List query params: `search`, `status`, `plan`, `page`, `size`.

When **any** of `search` / `status` / `plan` is set, `BusinessRepository.searchByNameOrOwner`
runs. `plan` is matched against **`plan.name`**, not `plan.slug` (e.g. `Pro`,
not `pro`). `GET /admin/businesses/plans` returns both `slug` and `name` —
use `name` for the list filter. Search matches business name, email, or
propriétaire full name (ILIKE).

`revenue` on each row is the same UTC month window as stats, formatted
`Locale.FRANCE` as `"%,d F"`. Owner name is the earliest `PROPRIETAIRE`
membership (`"-"` if none). `subscription` is built from the **latest
subscription by `createdAt`**, including cancelled/expired rows.

### Members and roles (per business)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/admin/businesses/{businessId}/members` | All memberships (incl. inactive) |
| `GET` | `/admin/businesses/{businessId}/roles` | Roles + sorted permission codes |
| `PATCH` | `/admin/businesses/{businessId}/members/{businessUserId}/role` | Change member role |
| `PATCH` | `/admin/businesses/{businessId}/roles/{roleId}/permissions` | Replace role permissions |

Demoting the last active `PROPRIETAIRE` is rejected:
*« Impossible de retirer le dernier rôle Administrateur de cette entreprise. »*

Permission replace is wipe-and-insert (`deleteByRoleId` then link each code).
Unknown codes → `BusinessRuleException` (*« Permission inconnue: … »*). An
empty `permissionCodes` list clears the role. Evicts `CachedRolePermissions`
for that `roleId`. Audited as `UPDATE` / `BusinessRole` with
`source=platform_admin`.

Member role changes are audited as `UPDATE` / `BusinessUser`.

### Stores (per business)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/admin/businesses/{businessId}/stores` | All stores (active and inactive) |
| `POST` | `/admin/businesses/{businessId}/stores` | Create (plan `maxStores`) |
| `PUT` | `/admin/businesses/{businessId}/stores/{storeId}` | Update |
| `DELETE` | `/admin/businesses/{businessId}/stores/{storeId}` | Delete |

Create calls `SubscriptionService.assertCanAddStore` (same limit as tenant
create). Mutations call `CachedLookups.evictAllStores()`. Admin list does
**not** apply `business_user_store` assignment; tenant `GET /stores` does
(empty assignment = all **active** stores — see `CachedLookups.storesForUser`).

### Users (platform-wide)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/admin/users` | Paged users |
| `POST` | `/admin/users/invite` | Add a member to a business |
| `PATCH` | `/admin/users/{id}/status` | `{ "active": true\|false }` |

List params: `search`, `status` (`active` / `disabled` / `inactive`), `role`,
`page`, `size`. Role filter aliases: propriétaire/admin → `PROPRIETAIRE`,
gestionnaire/manager → `GESTIONNAIRE`, caissier/seller → `CAISSIER`. Displayed
`role` / `business` are the **first** `business_user` row for that user, not
necessarily the propriétaire.

### Audit logs

`GET /admin/audit-logs` — see [audit-log.md](audit-log.md). Tenant members use
`GET /api/v1/audit-logs` (no `AUDIT_*` permission).

### Demo requests

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/admin/demo-requests` | Optional `status` filter |
| `POST` | `/admin/demo-requests/{id}/approve` | Provision tenant + trial |
| `POST` | `/admin/demo-requests/{id}/reject` | Optional `{ "reason": "…" }` (max 2000) |

Approve/reject only when status is pending. Approve provisions via
`AuthService.provisionTenantAfterDemoApproval` (14-day trial on plan slug
`pro`). If the request had no password hash, a reset token is emailed. Public
submit is `POST /api/v1/auth/demo-request` (not an admin route).

## Create business

```bash
curl -X POST "http://localhost:8080/api/v1/admin/businesses" \
  --header 'Authorization: Bearer <platform-admin-token>' \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "Boutique Demo",
    "email": "boutique@example.com",
    "phone": "+225 07 00 00 00 00",
    "address": "Abidjan",
    "planSlug": "trial",
    "ownerUserId": null
  }'
```

Constraints:

| Field | Rule |
|-------|------|
| `name`, `email` | Required. Email unique on `business`. |
| `planSlug` | Omit, blank, or `trial` → `createTrialForNewBusiness` (14 days, plan `pro`). Any other slug → paid `active` sub starting today (monthly). |
| `ownerUserId` | Optional. Must exist; linked as `PROPRIETAIRE`. If omitted, the business has **no** owner membership. |
| Default roles | `BusinessRoleBootstrapService.ensureDefaultRolesForBusiness` always runs. |

`Business.create` starts as status `trial`. A paid `planSlug` then
`activate()`s the business, clears `trialEndsAt`, and sets `trialUsedAt`.

## Assign plan vs renew

**Assign** (`PATCH .../plan`): body `{ "planSlug": "business", "billingCycle": "yearly" }`.
`billingCycle` defaults to `monthly` unless `yearly`. Cancels the current
access-granting subscription immediately and inserts a new `active` period
**starting today**. Rejects inactive plan slugs. Rejects if current store
count exceeds the target plan `maxStores` (unless unlimited / `0`).

**Renew** (`POST .../subscription/renew`): body optional
`{ "planSlug", "billingCycle" }`. If omitted, reuses the latest subscription's
plan and cycle. No latest sub and no `planSlug` →
*« Aucun abonnement existant — précisez un plan ou utilisez « Changer le plan ». »*

Period start (`anchor`):

| Latest sub | Anchor |
|------------|--------|
| none / trialing / not `active` | today |
| `active` and `currentPeriodEnd` ≥ today | that `currentPeriodEnd` (stack) |
| `active` and period already ended | today |

Then cancels any access-granting sub, inserts `active` for one month or year,
activates the business, clears trial fields.

## Invite user (admin)

```json
{
  "email": "caissier@example.com",
  "fullName": "Awa Kouassi",
  "role": "caissier",
  "businessId": "<uuid>"
}
```

- Enforces `maxUsers` when an `active`/`trialing` subscription has a finite
  limit. Does **not** check `featureRoleManagement` (unlike tenant
  `POST /business/users`).
- Existing platform user: membership only. New user: random 24-char password is
  hashed and **discarded** — no invitation email, password is not returned.
  The invitee must use `POST /api/v1/auth/forgot-password` (tenant invite
  *does* send `sendInvitationEmail`).
- Duplicate membership → 409.

`PATCH /admin/users/{id}/status` toggles `users.is_active` only (not
`business_user.is_active`). Body must include `"active"`.

## Business status

`PATCH .../status` with `{ "status": "suspended" | "active" }`. Other values
→ `IllegalArgumentException`. This is the **business** row status, not the
subscription status. SubscriptionRequiredFilter still keys off subscription
period for tenant APIs.

## Pitfalls

- Admin JWT `roleId` is null: do not expect tenant permission codes on the
  access token. Backoffice UI should key off `role === "PLATFORM_ADMIN"` /
  `platformAdmin`.
- List `plan` filter is **plan name**, not slug.
- Admin invite is silent (no mail). Prefer tenant invite when the user must
  set a password from email.
- Store assignment (`business_user_store`) is not a hard security boundary
  for POS/catalog; it only filters tenant `GET /stores` when the member has
  at least one assignment.
- `monthlyRevenue` / business `revenue` ignore sales after today (window ends
  tomorrow 00:00 UTC) and ignore non-`completed` sales.
- Deleting a store can fail on remaining FKs (sales, stock, expenses); the
  admin service does not pre-check dependents.

## Codepaths

- `admin/infrastructure/web/Admin*Controller`
- `admin/application/service/AdminBusinessService`, `AdminUserService`,
  `AdminStatsService`, `AdminStoreService`, `AdminBusinessMemberService`,
  `AdminBusinessRoleService`
- `identity/.../DemoRequestService`, `identity/.../AuthService`
- `tenant/.../SubscriptionUsageService`, `SubscriptionService`,
  `BusinessRoleBootstrapService`
- `identity/infrastructure/security/SecurityConfig`, `JwtService`
- `tenant/infrastructure/security/SubscriptionRequiredFilter`
