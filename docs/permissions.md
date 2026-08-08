# Permissions and Navigation

The backend uses role-based permissions scoped to a business. Permissions are
stored in the database, attached to business roles, and enforced by application
services before protected operations run.

## Codepaths

- `identity/domain/model/Permission.java` is the Java enum of permission codes
  used by services.
- `tenant/application/service/BusinessRoleBootstrapService.java` creates system
  roles and assigns default permissions for each business.
- `tenant/application/service/RoleManagementService.java` updates role
  permission assignments.
- `identity/application/NavigationPermissionRules.java` maps frontend navigation
  entries to required permission codes.
- `identity/infrastructure/web/PermissionsController.java` exposes the
  permission catalogue and current-user permission response.
- Migrations `V54` through `V59` create permission tables, seed labels, add
  commerce permissions, and add `REPORTS_READ`.

## System Roles

System roles use French role codes in the database:

| Role code | Intent |
| --- | --- |
| `PROPRIETAIRE` | Business owner. Broad tenant control, including subscription and team management. |
| `GESTIONNAIRE` | Manager. Operational access with fewer administrative permissions. |
| `CAISSIER` | Cashier. POS-focused access. |

Roles are business-scoped. A user's authorities are derived from their current
business role and embedded in the authenticated principal.

## Permission Catalogue

Permission codes are grouped by resource/action:

| Area | Codes |
| --- | --- |
| Products | `PRODUCTS_CREATE`, `PRODUCTS_READ`, `PRODUCTS_UPDATE`, `PRODUCTS_DELETE` |
| Categories | `CATEGORIES_CREATE`, `CATEGORIES_READ`, `CATEGORIES_UPDATE`, `CATEGORIES_DELETE` |
| Stock | `STOCK_READ`, `STOCK_INIT`, `STOCK_ADJUST` |
| Clients | `CLIENTS_CREATE`, `CLIENTS_READ`, `CLIENTS_UPDATE`, `CLIENTS_DELETE` |
| Suppliers | `SUPPLIERS_CREATE`, `SUPPLIERS_READ`, `SUPPLIERS_UPDATE`, `SUPPLIERS_DELETE` |
| Purchase orders | `PURCHASE_ORDERS_CREATE`, `PURCHASE_ORDERS_READ`, `PURCHASE_ORDERS_UPDATE`, `PURCHASE_ORDERS_DELETE` |
| Sales | `SALES_CREATE`, `SALES_READ`, `SALES_UPDATE`, `SALES_DELETE` |
| Expenses | `EXPENSES_CREATE`, `EXPENSES_READ`, `EXPENSES_UPDATE`, `EXPENSES_DELETE` |
| Delivery couriers | `DELIVERY_COURIERS_CREATE`, `DELIVERY_COURIERS_READ`, `DELIVERY_COURIERS_UPDATE`, `DELIVERY_COURIERS_DELETE` |
| Stores | `STORES_CREATE`, `STORES_READ`, `STORES_UPDATE`, `STORES_DELETE` |
| Dashboard/global | `GLOBAL_VIEW_READ`, `REPORTS_READ` |
| Subscription | `SUBSCRIPTION_READ`, `SUBSCRIPTION_UPDATE` |
| Business users | `BUSINESS_USERS_CREATE`, `BUSINESS_USERS_READ`, `BUSINESS_USERS_UPDATE`, `BUSINESS_USERS_DELETE` |
| Integrations | `API_KEYS_CREATE`, `API_KEYS_READ`, `API_KEYS_DELETE`, `WEBHOOKS_CREATE`, `WEBHOOKS_READ`, `WEBHOOKS_UPDATE`, `WEBHOOKS_DELETE` |
| Commerce connections | `COMMERCE_CONNECTIONS_CREATE`, `COMMERCE_CONNECTIONS_READ`, `COMMERCE_CONNECTIONS_UPDATE`, `COMMERCE_CONNECTIONS_DELETE` |

When adding a backend permission:

1. Add the code to `Permission.java`.
2. Add or update a Flyway migration to seed `app_permission`.
3. Decide default assignments for system roles.
4. Enforce the permission in the application service, not only in the
   controller.
5. Update `NavigationPermissionRules` if the permission controls a visible menu
   entry or route.
6. Add or update tests for navigation rules or permission-sensitive services.

## Navigation Rules

`NavigationPermissionRules.asMap()` returns the route/menu keys the frontend can
use after loading `/api/v1/permissions/me`.

Current backend navigation keys:

| Navigation key | Grants visibility when user has any of |
| --- | --- |
| `dashboard` | `SALES_READ`, `PRODUCTS_READ` |
| `pos` | `SALES_CREATE` |
| `products` | `PRODUCTS_READ` |
| `clients` | `CLIENTS_READ` |
| `suppliers` | `SUPPLIERS_READ` |
| `livreurs` | Any delivery-courier permission |
| `globalView` | `GLOBAL_VIEW_READ` |
| `expenses` | `EXPENSES_READ` |
| `reports` | `REPORTS_READ` |
| `settings` | `STORES_READ`, `SUBSCRIPTION_READ`, `BUSINESS_USERS_READ` |
| `settings:stores` | `STORES_READ` |
| `settings:profile` | `STORES_READ` |
| `settings:subscription` | `SUBSCRIPTION_READ` |
| `settings:users` | `BUSINESS_USERS_READ` |
| `settings:roles` | `BUSINESS_USERS_READ` |
| `settings:security` | `STORES_READ` |
| `settings:notifications` | `STORES_READ` |
| `backoffice` | Empty list; visible by frontend/platform-admin convention. |

The class comment notes that these rules must stay aligned with the client-side
fallback until the frontend loads `/permissions/me`. Treat backend and frontend
navigation rule updates as one change.

## Service Enforcement Pattern

Services receive the authenticated `UserPrincipal` and call the role permission
service before performing restricted work, for example:

```java
permissionService.require(p, Permission.PRODUCTS_READ);
```

Keep permission checks near the use case that owns the mutation/read. Controller
annotations alone are not enough because shared services may be reused by other
controllers or integration flows.

## Platform Admin vs Tenant Permissions

Platform admin access is controlled by Spring Security:

```text
/api/v1/admin/** -> ROLE_PLATFORM_ADMIN
```

Platform-admin routes are not governed by tenant role permissions and are
excluded from subscription blocking. Tenant APIs still require a tenant
business, a business role, and the matching permission.
