# Stores

Tenant boutiques live under `/api/v1/stores`. Platform operators use a separate admin API. Behavior below is taken from `StoreService`, `AdminStoreService`, `CachedLookups.storesForUser`, `SubscriptionService.assertCanAddStore`, and the `store` foreign keys.

Tenant scope is the `businessId` claim on the JWT. `X-Business-Id` is declared in `ApiConstants` and has no reader.

## Tenant API

| Method | Path | Permission |
| --- | --- | --- |
| `POST` | `/api/v1/stores` | `STORES_CREATE` |
| `GET` | `/api/v1/stores` | `STORES_READ` |
| `GET` | `/api/v1/stores/{id}` | `STORES_READ` |
| `PUT` | `/api/v1/stores/{id}` | `STORES_UPDATE` |
| `DELETE` | `/api/v1/stores/{id}` | `STORES_DELETE` |

Controllers do not use `@PreAuthorize`. `RolePermissionService.require` runs inside the service after the business-context check. Platform admins pass every permission check. Everyone else needs `hasBusinessAccess()` and a non-null `roleId`.

Seeded system roles (`V54__business_roles_permissions.sql` and `BusinessRoleBootstrapService` for a business that has no roles yet):

| Role | Store permissions |
| --- | --- |
| `PROPRIETAIRE` | create, read, update, delete |
| `GESTIONNAIRE` | create, read, update, delete |
| `CAISSIER` | `STORES_READ` only |

Custom roles can grant a different set. There is no store-specific plan feature. `feature_global_view` gates the dashboard global view, not these endpoints.

### Request and response

```json
{
  "name": "Boutique Plateau",
  "address": "Avenue 12, Abidjan",
  "phone": "+2250102030405",
  "isActive": true
}
```

| Field | Constraint |
| --- | --- |
| `name` | Required, max 255 characters (`store.name VARCHAR(255)`). Whitespace-only is rejected. The value is stored as sent, including surrounding spaces. |
| `address` | Optional text. |
| `phone` | Optional, max 50 characters. |
| `isActive` | See create vs update below. |

`StoreResponse` returns `id`, `businessId`, `name`, `address`, `phone`, `isActive`, `createdAt`, `updatedAt`.

Create returns HTTP 201. Update returns 200. Delete returns 204.

`StoreRepository.existsByBusinessIdAndName` and `Store.deactivate()` have no callers. Two stores in the same business may share a name. Deactivation is `isActive: false` on update.

## Create, update, and the active flag

`POST` calls `Store.create`, which always sets `isActive` to `true`. A create body with `"isActive": false` still inserts an active store.

`PUT` writes `name`, `address`, `phone`, and `isActive`. `StoreRequest` turns a missing or JSON `null` `isActive` into `true` before the service runs. Omitting the field on an inactive store reactivates it. Send `"isActive": false` to keep it inactive.

`GET /api/v1/stores/{id}` returns the row when it belongs to the JWT business, including inactive stores. A store from another business is HTTP 404 (`Store not found with id: …`). The handler does not check store assignments.

## List filtering and cache

`GET /api/v1/stores` calls `CachedLookups.storesForUser(businessId, userId)`:

1. Load stores with `is_active = true` for the business.
2. If that user has at least one `business_user_store` row, keep only those store ids.
3. If the user has no assignment rows, return every active store. A missing `business_user` row takes the same path.

The result is cached under `stores` with key `businessId:userId` (Caffeine, `expireAfterWrite` 10 minutes, max 1,000 entries, local to the process). Create, update, and delete call `evictAllStores()`, which clears every `stores` entry on that node. Another node can serve the previous list until the TTL.

`GET` by id is not cached.

## Plan limit

`assertCanAddStore` runs on tenant and admin create. It counts `findByBusinessId`, so inactive stores consume a slot. Deactivating a boutique does not free capacity. A successful delete does.

| Plan slug (`V32`) | `max_stores` | Meaning |
| --- | --- | --- |
| `starter` | 1 | Second create is rejected |
| `pro` | 3 | Fourth create is rejected |
| `business` | 0 | `Plan.isUnlimited` treats `0` as unlimited |

When no access-granting plan is resolved, the limit check is skipped (`Optional.ifPresent`). Access-granting statuses are `trialing` and `active`.

Over the limit, the service throws `BusinessRuleException`, which `GlobalExceptionHandler` maps to HTTP 422:

```text
Limite du plan atteinte : maximum {n} magasin(s). Passez à un plan supérieur.
```

Tenant routes still pass through `SubscriptionRequiredFilter` first. With no `trialing`/`active` subscription whose `currentPeriodEnd` is today or later, the filter returns HTTP 402 and this body (not a ProblemDetail):

```json
{
  "code": "SUBSCRIPTION_REQUIRED",
  "message": "Votre période d'essai est terminée. Veuillez souscrire à un plan pour continuer."
}
```

`/api/v1/admin/**` skips that filter. An admin create on a business with no current plan therefore hits the skipped limit check and can insert a store.

## Delete and foreign keys

Delete is a hard `repository.delete` of the tenant-owned row. There is no "deactivate instead" path in the service.

| Table | `store_id` | On store delete |
| --- | --- | --- |
| `product_store_stock`, `stock_movement`, `client_payment`, `purchase_order`, `sale` | required | Default `NO ACTION`. The delete fails. |
| `expense` | optional | Default `NO ACTION`. A row that points at the store blocks the delete. |
| `business_user_store` | required | `ON DELETE CASCADE` (`V49`) |
| `commerce_connection` | required | `ON DELETE CASCADE` (`V56`). Ingestion logs cascade from the connection. |

`GlobalExceptionHandler` has no `DataIntegrityViolationException` handler. A blocked delete becomes HTTP 500 with detail `An unexpected error occurred`. The database message stays in the server log.

A store that only has assignment rows and commerce connections can be deleted. Those connections and their ingestion logs are removed with it. Sales, stock, purchase orders, client payments, or linked expenses block the delete.

Duplicate ids in one assignment request can also hit a unique constraint and return HTTP 500. See assignments below.

## Staff assignments

Assignments are not part of the store resource. They replace a member's set:

- `PUT /api/v1/business/users/{businessUserId}/stores` with `{ "storeIds": ["…"] }` requires `BUSINESS_USERS_UPDATE`.
- `GET /api/v1/business/users/{businessUserId}/stores` requires `BUSINESS_USERS_READ`.

The replace deletes existing rows, then inserts each id that belongs to the JWT business. Ids from another business are HTTP 404. The response lists the assigned stores and includes inactive ones. `GET /api/v1/stores` still hides inactive stores.

`storeIds: []` clears assignments. With no rows left, `GET /api/v1/stores` returns every active store for that user. `GET …/stores` on the member returns an empty array in that case.

`UNIQUE(business_user_id, store_id)` rejects a repeated id in the same body with HTTP 500.

Assignment changes also call `evictAllStores()`.

## Admin API

`/api/v1/admin/businesses/{businessId}/stores` requires a platform-admin principal (`AccessDeniedException` → HTTP 403, `Platform admin only`). It addresses the path `businessId`, not the caller's JWT business.

| Method | Behavior |
| --- | --- |
| `GET` | Every store for that business, including inactive. No assignment filter. Unknown business → 404. |
| `POST` | Same plan count and always-active create as the tenant API. HTTP 201. |
| `PUT` | Same fields and `isActive` default as the tenant update. |
| `DELETE` | Same hard delete and foreign keys. HTTP 204. |

A platform-admin token used on `/api/v1/stores` still mutates the business stored in that JWT. Cross-tenant edits go through the admin paths.

## Related reads

Dashboard global view (`GLOBAL_VIEW_READ`, plus `feature_global_view` and `feature_reports` when a plan is present) loads every store row, inactive included, for display names. Per-store lines come from sales and expense aggregates in the requested period. Expenses with a null `store_id` are the « Communes » line. A store with neither sales nor expenses in the window has no line.

Stock, sales, and expenses reference `store_id` but do not re-check this list filter inside those services.

No store mutation writes an audit log.

## HTTP errors on these routes

| Situation | Status | Body |
| --- | --- | --- |
| Bean validation (`name`, `phone`, assignment `storeIds` null) | 400 | ProblemDetail title `Validation Error`, property `errors` |
| Unknown store or other-business id | 404 | ProblemDetail detail `Store not found with id: …` |
| Missing permission, or non-admin on the admin API | 403 | ProblemDetail detail from `AccessDeniedException` |
| Plan store limit | 422 | ProblemDetail detail is the French limit message |
| Expired or missing access-granting subscription (tenant routes) | 402 | `{ "code": "SUBSCRIPTION_REQUIRED", "message": "…" }` |
| Foreign-key or unique-constraint failure | 500 | ProblemDetail detail `An unexpected error occurred` |

## Codepaths

- `store/infrastructure/web/StoreController`
- `store/application/service/StoreService`
- `store/application/dto/StoreRequest`, `StoreResponse`
- `store/domain/model/Store`, `store/domain/repository/StoreRepository`
- `admin/infrastructure/web/AdminBusinessStoreController`
- `admin/application/service/AdminStoreService`
- `shared/infrastructure/cache/CachedLookups` (`storesForUser`, `evictAllStores`)
- `shared/infrastructure/config/CacheConfig`
- `tenant/application/service/SubscriptionService.assertCanAddStore`
- `tenant/application/service/BusinessUserService.assignStores`, `getAssignedStores`
- `tenant/infrastructure/security/SubscriptionRequiredFilter`
- `identity/application/service/RolePermissionService`
- `tenant/application/service/BusinessRoleBootstrapService`
- `db/migration/V1__initial_schema.sql`, `V32__update_plans_plans_abonnements.sql`, `V49__business_user_store_assignment.sql`, `V54__business_roles_permissions.sql`, `V56__commerce_order_integration.sql`
