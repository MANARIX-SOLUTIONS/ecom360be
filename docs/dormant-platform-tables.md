# Dormant platform tables and half-wired APIs

Several tables and cache names exist on `main` but have **no runtime readers
or writers** beyond JPA/Flyway. Treat them as schema leftovers, not as
working product features.

## Feature flags

| Item | Location |
| --- | --- |
| Table | `feature_flag` (`V1`, seed `V26`) |
| Entity / repo | `platform/domain/model/FeatureFlag`, `FeatureFlagRepository` |
| Cache name | `featureFlags` in `CacheConfig` |

Seeded keys (`ON CONFLICT DO NOTHING`): `pos_enabled`, `expenses_enabled`,
`reports_enabled` (all inserted `is_enabled = true`).

No service, controller, or `@Cacheable("featureFlags")` method exists.
Toggling a row in SQL does not hide POS, expenses, or reports. Those
capabilities are gated by **plan columns** (`feature_expenses`,
`feature_reports`, …) and RBAC permissions, not by this table.

## Platform config

| Item | Location |
| --- | --- |
| Table | `platform_config` (`V1`, seed `V3`) |
| Entity / repo | `platform/domain/model/PlatformConfig`, `PlatformConfigRepository` |
| Cache name | `platformConfig` in `CacheConfig` |

Seeded keys: `app_name` = `360 PME Commerce`, `default_currency` = `XOF`,
`maintenance_mode` = `false`.

No service reads these keys. `maintenance_mode` does not put the API in
maintenance. Currency/locale on a tenant come from `business.currency` /
`business.locale` (and are not editable via `/api/v1/business/me`).

`CacheConfig` pre-registers `featureFlags` and `platformConfig` so they
appear in actuator cache listings. `CachedLookups` only uses `plans`,
`categories`, and `stores`.

## Invoices

| Item | Location |
| --- | --- |
| Table | `invoice` (`V1`) |
| Entity / repo | `tenant/domain/model/Invoice`, `InvoiceRepository` |

`InvoiceRepository.findByBusinessIdOrderByCreatedAtDesc` is unused.
`changePlan` / `cancel` / `reactivate` on `/api/v1/subscription` do not
insert invoice rows. `Invoice.markPaid` / `isOverdue` have no callers.

`isOverdue()` is true only when `status == "draft"` and `dueDate` is in the
past — not a paid/unpaid check.

Do not build billing UI against this table until a writer exists.

## In-app notifications (read API only)

`/api/v1/notifications` is authenticated and implemented:

| Method | Behavior |
| --- | --- |
| `GET /notifications` | Page by `userId`; `unreadOnly=true` filters unread; `size` capped at 100 |
| `GET /notifications/unread-count` | `{ "count": <long> }` |
| `PATCH /notifications/{id}/read` | Marks one row owned by the caller |
| `POST /notifications/mark-all-read` | `{ "marked": <int> }` |
| `GET /notifications/preferences` | Returns all types in `NotificationTypes.ALL`, default **enabled** when no row exists |
| `PUT /notifications/preferences` | Upserts only keys present in the body that are in `ALL`; unknown keys ignored |

Known types: `low_stock`, `payment_received`, `subscription`, `system`.

`NotificationService.createNotification(...)` exists and respects
preferences (`missing row` ⇒ enabled), but **nothing on `main` calls it**.
List/unread endpoints therefore stay empty unless rows are inserted by
hand. Feature branch `notification_management` (PR #45) is the expected
writer; do not document that branch as shipped.

There is no notification permission check beyond JWT + current user id.

## What actually gates product behavior

| Concern | Source of truth on `main` |
| --- | --- |
| Plan limits / feature toggles | `plan.*` via `SubscriptionService.getPlanForBusiness` |
| Tenant access after expiry | `SubscriptionRequiredFilter` (`402 SUBSCRIPTION_REQUIRED`) |
| Screen/API permissions | `Permission` + `business_role_permission` |
| Runtime caches in use | `plans`, `categories`, `stores` (`CachedLookups`) |

## Implementation map

- Unused entities: `platform/domain/model/*`, `tenant/domain/model/Invoice`
- Registered caches: `shared/infrastructure/config/CacheConfig`
- Used caches: `shared/infrastructure/cache/CachedLookups`
- Notification HTTP: `notification/infrastructure/web/NotificationController`
- Notification write (uncalled): `NotificationService.createNotification`
