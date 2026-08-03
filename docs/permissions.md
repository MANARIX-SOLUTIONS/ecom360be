# Permissions and roles

How authorization works in ecom360be. Verified against `Permission`, `RolePermissionService`, `CachedRolePermissions`, `SecurityConfig`, `NavigationPermissionRules`, and Flyway `V54`/`V55`/`V57`/`V59`.

## Layers

1. **Spring Security** (`SecurityConfig`) — JWT auth; public paths; `/api/v1/admin/**` requires `ROLE_PLATFORM_ADMIN`.
2. **Subscription filter** — `SubscriptionRequiredFilter` runs after JWT (expired/blocked tenants get HTTP 402 on most routes; public/auth recovery paths bypass).
3. **Service checks** — controllers call services that use `RolePermissionService.require` / `requireAny` against `Permission` enum codes stored per business role.

Platform admins (`UserPrincipal.isPlatformAdmin()`) pass every `RolePermissionService` check. Business users need `hasBusinessAccess()` and a non-null `roleId`.

## French role codes

Canonical codes (uppercase) after `V55__business_role_codes_fr.sql`:

| Code | Name | Seeded permissions (system roles) |
|------|------|-----------------------------------|
| `PROPRIETAIRE` | Propriétaire | All `app_permission` rows |
| `GESTIONNAIRE` | Gestionnaire | All except `SUBSCRIPTION_UPDATE` and `BUSINESS_USERS_DELETE` (V54); later migrations also grant commerce + reports |
| `CAISSIER` | Caissier | Narrow set (below) |

README on `main` still lists lowercase `proprietaire` / `gestionnaire` / `caissier` — those are **obsolete**. JWT / DB use the uppercase French codes.

`V54` CAISSIER seed:

- `PRODUCTS_READ`, `CATEGORIES_READ`, `STOCK_READ`, `CLIENTS_READ`, `STORES_READ`
- `SALES_CREATE`, `SALES_READ`, `SALES_UPDATE`, `SALES_DELETE`

Later grants:

- `V59` — `REPORTS_READ` for `PROPRIETAIRE`, `GESTIONNAIRE`, and `CAISSIER`
- `V57` — `COMMERCE_CONNECTIONS_*` for `PROPRIETAIRE` and `GESTIONNAIRE` only (not CAISSIER)

Custom roles can diverge: permissions are rows in `business_role_permission`, not hardcoded switch statements.

## How `require` works

```text
RolePermissionService.can(principal, Permission)
  → false if principal null / no business / no roleId
  → true if platform admin
  → else CachedRolePermissions.codesForRole(roleId).contains(perm.name())
```

`require` / `requireAny` throw `AccessDeniedException` with a French message naming the missing permission.

Permission codes are the enum names (`SALES_CREATE`, `GLOBAL_VIEW_READ`, …). Catalogue is exposed to the UI via permissions endpoints built with `PermissionsResponseBuilder` + `NavigationPermissionRules`.

## Navigation contract

`NavigationPermissionRules.asMap()` keys (menu / route) → user needs **any** listed permission:

| Key | Needs any of |
|-----|----------------|
| `dashboard` | `SALES_READ`, `PRODUCTS_READ` |
| `pos` | `SALES_CREATE` |
| `products` | `PRODUCTS_READ` |
| `clients` | `CLIENTS_READ` |
| `suppliers` | `SUPPLIERS_READ` |
| `livreurs` | any `DELIVERY_COURIERS_*` |
| `globalView` | `GLOBAL_VIEW_READ` |
| `expenses` | `EXPENSES_READ` |
| `reports` | `REPORTS_READ` |
| `settings` / subkeys | stores / subscription / users as listed in source |
| `backoffice` | empty list (platform UI; separate admin role) |

Note: dashboard navigation allows `SALES_READ` **or** `PRODUCTS_READ`, while `DashboardService` also accepts `REPORTS_READ` via `requireAny`.

## Sales / analytics permission map

| Area | Check |
|------|--------|
| Create sale | `SALES_CREATE` |
| List/get sale | `SALES_READ` |
| Update sale | `SALES_UPDATE` |
| Void sale | `SALES_DELETE` |
| Dashboard KPIs / slices | any of `SALES_READ`, `PRODUCTS_READ`, `REPORTS_READ` |
| Global store view | `GLOBAL_VIEW_READ` **and** plan `featureGlobalView` **and** reports feature |

## Public vs protected

`SecurityConfig` public (no JWT):

- `/api/v1/auth/login`, `demo-request`, `refresh`, `forgot-password`, `reset-password`
- `/api/v1/public/**` (includes commerce webhooks + public product images / logos)
- Swagger / OpenAPI paths
- `/actuator/health/**`, `/actuator/info`

Everything else authenticated. Admin APIs additionally require platform-admin role.

## Codepaths

- `identity/domain/model/Permission.java`
- `identity/application/service/RolePermissionService.java`
- `identity/application/service/CachedRolePermissions.java`
- `identity/application/NavigationPermissionRules.java`
- `identity/infrastructure/security/SecurityConfig.java`
- `db/migration/V54__business_roles_permissions.sql`, `V55__business_role_codes_fr.sql`, `V57__commerce_hmac_and_permissions.sql`, `V59__reports_read_permission.sql`
