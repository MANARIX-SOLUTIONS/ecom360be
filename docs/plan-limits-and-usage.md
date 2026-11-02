# Plan limits and usage

How `GET /api/v1/subscription/usage` counts seats, and how create paths enforce the same plan. Numeric limits and flags below are the rows written by `V32`, `V33`, `V51`, and `V53` for slugs `starter`, `pro`, and `business`. A limit of `0` means unlimited (`Plan.isUnlimited`).

The current plan is the newest `trialing` or `active` subscription for the business (`createdAt` desc). `SubscriptionService.getPlanForBusiness` then drops it when `currentPeriodEnd` is before today in the JVM default zone, and persists that transition. There is no separate plan lookup per store.

`SubscriptionRequiredFilter` uses the same status and date test but does not write the row. It returns HTTP 402 `{ "code": "SUBSCRIPTION_REQUIRED", "message": "..." }` on tenant routes when that subscription is missing. It skips `/api/v1/subscription`, `/api/v1/admin`, and `/api/v1/public/`. On those skipped paths, and only there, a missing or just-expired plan makes every `ifPresent` limit check a no-op.

Lazy expiry sets the subscription to `expired`, or to `cancelled` when `cancelAtPeriodEnd` is set. It does not update `business.status` or `trial_used_at`: `isTrialing()` is evaluated after `expire()` / `markCancelled()` has already changed the status. The daily job (`app.subscription.expiration-cron`, default `0 0 2 * * ?`) records `wasTrialing` first and does set `business.status` to `expired` for businesses still in `trial` or `active`. Cancel-at-period-end does not change `business.status` in either path.

## Usage endpoint

| Method | Path | Who |
| --- | --- | --- |
| `GET` | `/api/v1/subscription/usage` | Caller needs `SUBSCRIPTION_READ`. Propriétaire and gestionnaire have it (`V54`, `BusinessRoleBootstrapService`). Caissier does not. |
| `GET` | `/api/v1/admin/businesses/{id}/subscription/usage` | `UserPrincipal.isPlatformAdmin()` only. Same counters, for that business id. |

`RolePermissionService` treats platform admins as allowed for every permission. A principal with no business access (not a platform admin) gets an all-zero body and no 403. A refused permission is HTTP 403 `ProblemDetail` titled `Forbidden`.

`GET /api/v1/subscription/me` is 204 when no access-granting subscription remains. The usage payload still returns `0` limits in that case, and `SubscriptionUsageResponse.is*Unlimited()` treats `0` as unlimited, so usage alone does not distinguish "no plan" from "unlimited".

```json
{
  "usersCount": 2,
  "usersLimit": 2,
  "storesCount": 1,
  "storesLimit": 1,
  "productsCount": 40,
  "productsLimit": 100,
  "clientsCount": 10,
  "clientsLimit": 50,
  "suppliersCount": 3,
  "suppliersLimit": 10,
  "salesThisMonth": 120,
  "salesLimit": 500
}
```

`productsCount`, `clientsCount`, and `suppliersCount` are cast to `int`. `salesThisMonth` stays a `long`. The sales window is the JVM default zone: `[first day of this month 00:00, first day of next month 00:00)`. It is not UTC.

## Counters versus gates

Quota failures throw `BusinessRuleException` (HTTP 422, title `Business Rule Violation`). Feature denials are 422 or 403 depending on the call site, listed below. Every gate is skipped when `getPlanForBusiness` is empty.

| Resource | Usage count | Create / invite gate | What frees a slot |
| --- | --- | --- | --- |
| Users | Active `business_user` rows | Same count, in `BusinessUserService.invite` | Nothing in the tenant API. Invite does not deactivate memberships. |
| Stores | `findByBusinessId` (inactive included) | Same count, `StoreService.create` and `AdminStoreService` | Hard delete. `isActive=false` does not. |
| Products | `countByBusinessId` (inactive included) | `countByBusinessIdAndIsActive(..., true)` only | Soft delete (`isActive=false`) frees the create slot and still counts in usage. |
| Clients | `countByBusinessId` (inactive included) | Same count | Hard delete. Deactivating does not. |
| Suppliers | `countByBusinessId` (inactive included) | Same count | Hard delete. Deactivating does not. |
| Sales this month | All sales in the window, any status | Same count, POS `createSale` and commerce `createSaleFromImport` | Nothing. Void keeps the row. Sale update does not check the monthly cap. |

Dashboard totals are a third definition: products are all rows, clients and suppliers are active only, stores are all rows. Do not treat the dashboard cards as the usage payload.

Admin invite (`AdminUserService.invite`) counts active members against `max_users`, but it does not call `getPlanForBusiness`. It reads the newest `active`/`trialing` row and ignores `currentPeriodEnd`, and it does not apply the caissier-only invite rule.

## Seeded numeric limits

| | Starter | Pro | Business |
| --- | --- | --- | --- |
| `max_users` | 2 | 5 | 0 |
| `max_stores` | 1 | 3 | 0 |
| `max_products` | 100 | 500 | 0 |
| `max_sales_per_month` | 500 | 2000 | 0 |
| `max_clients` | 50 | 0 | 0 |
| `max_suppliers` | 10 | 0 | 0 |
| `price_monthly` / `price_yearly` | 15000 / 120000 | 25000 / 250000 | 35000 / 350000 |
| `data_retention_months` | 3 | 12 | 0 |

Prices are the integer columns from `V32`. The API does not attach a currency code.

## Feature flags

Flags that are only copied onto `PlanResponse` (and the plan cache) and are not read by a service: `feature_export_pdf`, `feature_export_excel`, `feature_priority_support`, `feature_account_manager`.

| Flag | Starter / Pro / Business | Enforcement |
| --- | --- | --- |
| `feature_expenses` | off / on / on | 422 on expense and expense-category **create** only. List, update, and delete stay open. |
| `feature_client_credits` | off / on / on | 422 on `POST /clients/{id}/payments` and on sale **update** when `paymentMethod` is `credit`. POS create rejects `credit` before the plan check. |
| `feature_multi_payment` | off / on / on | 422 when `paymentMethod` is `wave` or `orange_money`, on create and on update. |
| `feature_reports` | off / on / on | Dashboard does not 403. It forces the selected period to today and keeps recent sales from today only. `GET /dashboard/global` calls `requireFeatureReports` and returns 403. |
| `feature_advanced_reports` | off / off / on | Dashboard gross margin and top-margin products are filled only when the flag is true. Otherwise those fields stay null / empty. |
| `feature_stock_alerts` | off / on / on | No error. Low-stock lists are empty when a plan exists and the flag is false. No plan still shows low stock. |
| `feature_global_view` | off / on / on | 403 on `GET /dashboard/global` when the flag is false. That route also requires `feature_reports`. |
| `feature_supplier_tracking` | off / on / on | 403 on purchase-order create, get, list, and status change. Supplier CRUD uses `max_suppliers` only. |
| `feature_delivery_couriers` | off / on / on | 403 on courier and delivery operations (`CourierService`, `DeliveryService`). |
| `feature_api` | off / off / on | 403 on API keys, outbound webhook registry, and commerce connections. |
| `feature_custom_branding` | off / off / on | 403 on logo upload and on a non-empty logo URL. Clearing the logo skips the flag. Owner role (or platform admin) is required either way. |
| `feature_role_management` | off / off / on | 422 on tenant invite when the normalized role is not `CAISSIER`. Blank role normalizes to `CAISSIER` (`RoleCodeNormalizer`), so it is allowed. `RoleManagementService` create and permission assignment do not read this flag. |

`data_retention_months` of 0 disables the window. Otherwise the floor is today minus that many months, JVM default zone:

- Sale get returns 404 when `createdAt` is before the floor. Sale update and void throw 422 (`Cette vente est hors de la période d'historique de votre plan.`). Sale list replaces a null or earlier `from`.
- Dashboard period start is clamped, after the starter "today only" override.
- Expense get, update, and delete return 404 when `expenseDate` is before the floor. Expense list applies the floor only when both `month` and `year` are set. An unfiltered list returns older rows.

## Code paths

- `SubscriptionUsageService`, `SubscriptionController`, `AdminBusinessController.getSubscriptionUsage`
- `SubscriptionService.getPlanForBusiness`, `assertCanAddStore`, `requireFeatureReports`, `requireFeatureApi`, `requireSupplierTracking`, `clampPeriodStartToRetention`, `clampSaleHistoryFrom`, `isBeforeDataRetention`, `notExpiredOrExpireLazy`
- Create gates: `BusinessUserService.invite`, `AdminUserService.invite`, `StoreService`, `AdminStoreService`, `ProductService.create` / `delete`, `ClientService`, `SupplierService`, `SaleService.createSale`, `createSaleFromImport`, `updateSale`
- Flag call sites: `ExpenseService`, `DashboardService`, `PurchaseOrderService`, `CourierService`, `DeliveryService`, `ApiKeyService`, `WebhookService`, `CommerceConnectionService`, `BusinessProfileService`
- Seeds: `V32__update_plans_plans_abonnements.sql`, `V33__add_plan_retention_features.sql`, `V51__delivery_couriers_and_plan_feature.sql`, `V53__plan_feature_global_view.sql`
