# Permissions and RBAC

Authorization is tenant-scoped and permission based. Each business has roles in
`business_role`, each role links to `app_permission`, and users reference a role
through `business_user.role_id`.

Primary codepaths:

- `identity/domain/model/Permission.java`
- `tenant/application/service/RoleManagementService.java`
- `identity/application/service/RolePermissionService.java`
- `identity/application/service/CachedRolePermissions.java`
- `identity/application/NavigationPermissionRules.java`
- `identity/infrastructure/web/PermissionsController.java`
- `identity/infrastructure/web/MePermissionsController.java`
- `tenant/infrastructure/web/RoleController.java`
- `src/main/resources/db/migration/V54__business_roles_permissions.sql`
- `src/main/resources/db/migration/V58__app_permission_labels.sql`
- `src/main/resources/db/migration/V59__reports_read_permission.sql`

## Data model

| Table | Purpose |
| --- | --- |
| `app_permission` | Global permission catalog. Codes match `Permission` enum values. |
| `business_role` | Tenant-local roles. Includes system roles and custom roles. |
| `business_role_permission` | Role-to-permission join table. |
| `business_user` | Links a user to a business and one business role. |

System role codes:

- `PROPRIETAIRE`
- `GESTIONNAIRE`
- `CAISSIER`

Custom role codes are generated from the display name as `CUSTOM_*`, truncated
to 40 characters, and made unique per business.

## Permission catalog

Permission codes are grouped by resource/action, for example:

- `PRODUCTS_CREATE`, `PRODUCTS_READ`, `PRODUCTS_UPDATE`, `PRODUCTS_DELETE`
- `SALES_CREATE`, `SALES_READ`, `SALES_UPDATE`, `SALES_DELETE`
- `BUSINESS_USERS_CREATE`, `BUSINESS_USERS_READ`,
  `BUSINESS_USERS_UPDATE`, `BUSINESS_USERS_DELETE`
- `SUBSCRIPTION_READ`, `SUBSCRIPTION_UPDATE`
- `DELIVERY_COURIERS_CREATE`, `DELIVERY_COURIERS_READ`,
  `DELIVERY_COURIERS_UPDATE`, `DELIVERY_COURIERS_DELETE`
- `COMMERCE_CONNECTIONS_CREATE`, `COMMERCE_CONNECTIONS_READ`,
  `COMMERCE_CONNECTIONS_UPDATE`, `COMMERCE_CONNECTIONS_DELETE`
- `GLOBAL_VIEW_READ`
- `REPORTS_READ`

`REPORTS_READ` is intentionally distinct from dashboard and sales permissions.

## APIs

All routes require JWT unless otherwise stated.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/permissions` | List permission catalog with labels/categories/sort order |
| `GET` | `/api/v1/permissions/me` | Effective permissions and navigation rules |
| `GET` | `/api/v1/me/permissions` | Alias for `/permissions/me` |
| `GET` | `/api/v1/roles` | List roles for current business |
| `POST` | `/api/v1/roles` | Create custom role |
| `POST` | `/api/v1/roles/{id}/permissions` | Replace all permissions for a role |

`RoleManagementService` enforces:

- business context is required;
- reading roles requires `BUSINESS_USERS_READ`;
- creating roles and replacing permissions require `BUSINESS_USERS_UPDATE`;
- unknown permission codes are rejected;
- replacing role permissions evicts the cached permission set for that role.

## Effective permission response

`GET /api/v1/permissions/me` and `GET /api/v1/me/permissions` return:

```json
{
  "role": "GESTIONNAIRE",
  "permissions": ["PRODUCTS_READ", "SALES_CREATE"],
  "navigationRules": {
    "dashboard": ["SALES_READ", "PRODUCTS_READ"],
    "pos": ["SALES_CREATE"]
  }
}
```

`navigationRules` maps frontend navigation keys to permission codes. A user may
see a route when they have at least one code listed for that key.

## Navigation contract

`NavigationPermissionRules` is the backend source for route visibility rules
returned to clients. It must remain aligned with the frontend fallback contract.
The test `NavigationPermissionRulesTest` protects the expected keys.

Current keys:

| Navigation key | Grants access when user has one of |
| --- | --- |
| `dashboard` | `SALES_READ`, `PRODUCTS_READ` |
| `pos` | `SALES_CREATE` |
| `products` | `PRODUCTS_READ` |
| `clients` | `CLIENTS_READ` |
| `suppliers` | `SUPPLIERS_READ` |
| `livreurs` | Any `DELIVERY_COURIERS_*` permission |
| `globalView` | `GLOBAL_VIEW_READ` |
| `expenses` | `EXPENSES_READ` |
| `reports` | `REPORTS_READ` |
| `settings` | `STORES_READ`, `SUBSCRIPTION_READ`, or `BUSINESS_USERS_READ` |
| `settings:stores` | `STORES_READ` |
| `settings:profile` | `STORES_READ` |
| `settings:subscription` | `SUBSCRIPTION_READ` |
| `settings:users` | `BUSINESS_USERS_READ` |
| `settings:roles` | `BUSINESS_USERS_READ` |
| `settings:security` | `STORES_READ` |
| `settings:notifications` | `STORES_READ` |
| `backoffice` | Empty list; handled outside tenant permissions |

## Enforcement pattern

Services enforce permissions through `RolePermissionService`:

```java
permissionService.require(p, Permission.BUSINESS_USERS_UPDATE);
permissionService.requireAny(p, Permission.SALES_READ, Permission.PRODUCTS_READ);
```

`RolePermissionService` grants all permissions to platform admins, denies users
without business access, and otherwise checks cached permission codes for
`UserPrincipal.roleId()`.

## Common pitfalls

- Do not check legacy role strings such as `proprietaire`; use business role
  codes and permission checks.
- When adding a new route that needs frontend visibility, update
  `NavigationPermissionRules` and its test.
- When adding a new permission, update the enum and migration seed/labels so
  `/permissions` returns a complete catalog.
- Replacing role permissions is destructive by design: clients should send the
  complete desired permission list.
