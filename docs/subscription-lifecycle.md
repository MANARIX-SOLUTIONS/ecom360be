# Subscription lifecycle

How a tenant gets a trial, switches plan, cancels, and loses access. Verified against `SubscriptionController`, `SubscriptionService`, `Subscription`, `SubscriptionStatus`, `SubscriptionExpirationJob`, `SubscriptionRequiredFilter`, `SubscriptionRepository`, `Business`, `BusinessRoleBootstrapService`, `AuthService.provisionTenantAfterDemoApproval`, `AdminBusinessService`, `CachedLookups.activePlans`, `CacheConfig`, and migrations `V32`, `V45`, `V46`, `V54`.

Numeric limits and per-feature enforcement live on the plan row (`Plan.isUnlimited` is `limit == 0`). This page is the state machine. It does not restate every flag.

## Intent

- Give each new business one `pro` trial, then let a propriétaire replace that row with an `active` period.
- Keep tenant APIs open only while the newest `trialing` or `active` row still includes today.
- Leave `/api/v1/subscription/**` reachable after access ends so that same user can subscribe again.

There is no checkout on this branch. `changePlan` does not call a payment provider, does not prorate, and does not insert an `invoice` row. The `invoice` table is seeded (`V7`) and has no Java writer. `SubscriptionStatus.CAN_SUBSCRIBE`, `past_due`, `paused`, and `incomplete` are unused. `grace_period_ends_at` is never set.

## Who can call the API

JWT required. `SubscriptionRequiredFilter` does not block `/api/v1/subscription`.

| Call | Permission | System roles |
| --- | --- | --- |
| `GET /usage`, `GET /me`, `GET /plans` | `SUBSCRIPTION_READ` | `PROPRIETAIRE`, `GESTIONNAIRE` (`V54` and `BusinessRoleBootstrapService`) |
| `POST /change`, `POST /cancel`, `POST /reactivate` | `SUBSCRIPTION_UPDATE` | `PROPRIETAIRE` only. Gestionnaire is excluded in both places. |

`CAISSIER` has neither code on `V54` or in `CAISSIER_PERMISSIONS`. A missing permission is HTTP 403 `ProblemDetail` titled `Forbidden`, detail `Accès refusé : permission SUBSCRIPTION_UPDATE requise pour votre rôle` (or `SUBSCRIPTION_READ`).

`requireBiz` throws that same 403 with detail `Business context required` when `UserPrincipal.hasBusinessAccess()` is false. That method is true when `businessId != null` **or** the caller is a platform admin. A platform-admin token with a null `businessId` therefore passes the check. `changePlan` then inserts `business_id = null` into a `NOT NULL` column. The catch-all handler returns HTTP 500 `An unexpected error occurred`. Login only builds a token after it finds an active accepted membership, so a normal login does not take this path.

Platform admins pass every `RolePermissionService` check. This API still uses the business id on the token. Cross-tenant plan changes are `PATCH /api/v1/admin/businesses/{id}/plan`.

Nav key `settings:subscription` is `SUBSCRIPTION_READ`. That is a menu hint, not an extra server check.

## When access is on

A row grants access when `status` is `trialing` or `active` (`SubscriptionStatus.ACCESS_GRANTING`) and `currentPeriodEnd` is today or later. The comparison is `end.isBefore(LocalDate.now())`, so the end date itself is included. `LocalDate.now()` uses the JVM default zone, not UTC.

`SubscriptionRequiredFilter` reads that row and does not write. When it is missing, every other tenant route returns HTTP 402, `Content-Type: application/json;charset=UTF-8`:

```json
{
  "code": "SUBSCRIPTION_REQUIRED",
  "message": "Votre période d'essai est terminée. Veuillez souscrire à un plan pour continuer."
}
```

The sentence is fixed. It is also returned after a paid plan ends or an immediate cancel. The body is not `ProblemDetail`.

Paths that skip the filter: `/api/v1/subscription`, `/api/v1/admin`, `/api/v1/public/`. A null `businessId` skips it too. The filter does not read `business.status`. `PATCH /api/v1/admin/businesses/{id}/status` with `suspended` does not by itself cause 402.

## Trial

`createTrialForNewBusiness` is not an HTTP route. Callers on this branch:

- `AuthService.provisionTenantAfterDemoApproval` after a demo approval
- `AdminBusinessService.create` when `planSlug` is omitted, blank, or `trial`

`Business.hasUsedTrial()` is `trialUsedAt != null`. If it is set, the method throws HTTP 422 `L'essai gratuit n'est utilisable qu'une seule fois. Veuillez souscrire à un plan.` Creating the trial does not set `trialUsedAt`.

The row is `status=trialing`, `billingCycle=monthly`, `currentPeriodStart=today`, `currentPeriodEnd=today.plusDays(14)`. The plan is slug `pro`, or, if that slug is missing, the cheapest active plan (`priceMonthly` ascending). No active plan at all is HTTP 404 `Plan not found: any`. The business is set to `status=trial` and `trialEndsAt` equal to that end date. No subscription audit event is written.

## Tenant endpoints

| Method | Path | Success |
| --- | --- | --- |
| `GET` | `/api/v1/subscription/usage` | 200 usage payload. See counters below. |
| `GET` | `/api/v1/subscription/me` | 200 `SubscriptionResponse`, or 204 with an empty body |
| `GET` | `/api/v1/subscription/plans` | 200 list of active plans, cheapest monthly price first |
| `POST` | `/api/v1/subscription/change` | 200 the new `SubscriptionResponse` |
| `POST` | `/api/v1/subscription/cancel` | 204 |
| `POST` | `/api/v1/subscription/reactivate` | 200 the same row |

`GET /me` is 204 when no access-granting row remains after the lazy expire step below. `daysRemaining` is `max(0, whole days from today to currentPeriodEnd)` in the JVM zone. `isTrialing` is true only when `status` is still `trialing`.

`GET /plans` is `CachedLookups.activePlans`, cache name `plans`, key `active`, 10 minutes after write (`CacheConfig`). There is no evict method for that cache. The private `SubscriptionService.toPlanResponse` is unused. Each `PlanResponse` includes prices, the six numeric limits, every feature boolean, and `dataRetentionMonths` (null stored as 0). Prices are integers with no currency code.

`GET /usage` counts seats against the current plan. When `getPlanForBusiness` is empty the payload still returns and every limit is `0`, which `SubscriptionUsageResponse` treats as unlimited. Usage alone does not mean "no plan".

## Change plan

```bash
curl -X POST http://localhost:8080/api/v1/subscription/change \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"planSlug":"business","billingCycle":"yearly"}'
```

`ChangePlanRequest.planSlug` is required, max 50 characters, and is not trimmed. `billingCycle` null or blank becomes `monthly` in the record constructor. The service keeps `yearly` only when the value equals `yearly` ignoring case. `annual` is stored as `monthly`.

Unknown slug: HTTP 404 `Plan not found: {slug}`. Inactive plan: HTTP 422 `Plan is not available`.

The service loads the single newest subscription by `createdAt`, any status. It does not call the lazy expire helper first.

| Newest row | Result |
| --- | --- |
| Missing, or status other than `trialing` / `active` | Insert a new `active` row. Older rows are left as they are. |
| `trialing` or `active`, same `planId` and same cycle | HTTP 422 `Already on this plan and billing cycle`. The period end is not considered. |
| `trialing` or `active`, different plan or cycle | `cancelImmediate()` on that row, then insert a new `active` row. |

The new period starts today. The end is `today.plusMonths(1)` or `today.plusYears(1)`, and that end date is still included. There is no credit for the cancelled remainder.

The business is then `activate()`d (`status=active`), `trialEndsAt` cleared, and `trialUsedAt` set to `Instant.now()`. That stamp is what makes `hasUsedTrial()` true. It happens on every successful change, including a later switch between paid plans.

Consequences:

- A demo trial is `pro` + `monthly`. `POST /change` with that same pair is rejected for the whole trial, and it is still rejected after the end date until something flips the status off `trialing`. Switching to `yearly`, or to `starter` / `business`, works immediately and replaces the trial.
- `changePlan` does not compare current stores, users, or products with the target limits. `AdminBusinessService.assignPlan` does reject a target whose `maxStores` is below the store count (0 means unlimited). The tenant route does not. After a move to starter (`max_stores = 1` in `V32`), existing extra stores remain and `assertCanAddStore` blocks the next create.
- Only the newest row is cancelled. An older `trialing` row is not. `GET /me` and the filter use the newest access-granting row, so the new period wins. If that new row is later cancelled immediately, the older `trialing` row becomes current again while its end date is still ahead.
- No `invoice`, no audit row.

## Cancel and reactivate

`POST /cancel` with no body, `{}`, or `"atPeriodEnd": null` keeps access: `CancelSubscriptionRequest` defaults the flag to true. The current access-granting row (newest `trialing` or `active`, **not** date-checked) gets `cancelAtPeriodEnd=true`. Status and `cancelledAt` stay unchanged. No such row is HTTP 404 `Subscription not found: current`.

`"atPeriodEnd": false` calls `cancelImmediate()`: `status=cancelled`, `cancelledAt=today`, `cancelAtPeriodEnd=false`. The business row is not updated. The filter 402s other tenant routes on the next request. `GET /me` is 204. `POST /reactivate` does not undo this, because the flag is already false.

```bash
curl -X POST http://localhost:8080/api/v1/subscription/reactivate \
  -H "Authorization: Bearer $TOKEN"
```

Reactivate loads the newest row of any status.

| Condition | HTTP 422 detail |
| --- | --- |
| `cancelAtPeriodEnd` is false | `No subscription to reactivate` |
| Today is after `currentPeriodEnd` | `Subscription period has ended` |

Today equal to the end date is still allowed (`isAfter` is false). Success only sets `cancelAtPeriodEnd` back to false. It does not change `status`, and it does not restore a row the job has already marked `cancelled` (that path clears the flag).

## Two expiration paths

Both treat the end date as still valid. Both use the JVM zone. Neither path is a distributed lock. `@EnableScheduling` is on `Ecom360Application`, and every instance runs `app.subscription.expiration-cron` (default `0 0 2 * * ?`, override `SUBSCRIPTION_EXPIRATION_CRON`). The cron has no zone attribute. After one instance commits, the queries no longer match those rows.

### Lazy flip

`notExpiredOrExpireLazy` runs from `getCurrent`, `getPlanForBusiness`, and `getPlanSlugForBusiness`. The method is annotated `@Transactional`, but those callers are inside `SubscriptionService` and invoke it directly, so that annotation does not start a transaction. `repository.save` still commits on its own.

If `currentPeriodEnd` is before today:

- `cancelAtPeriodEnd` true → `markCancelled()` (`cancelled`, `cancelledAt=today`, flag cleared)
- otherwise → `expire()` (`expired`, `expiredAt=today`, flag cleared)

The following `if (sub.isTrialing())` updates `business.status` and `trialUsedAt`. It is unreachable: `expire()` and `markCancelled()` have already changed `status`. Lazy expiration does not stamp `trial_used_at` and does not change `business.status`.

The filter returns 402 before ordinary tenant services run, so a sale or product call does not flip the row. These calls do, because they are outside that block:

- `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh` (`getPlanSlugForBusiness`)
- `GET /api/v1/subscription/me` and `GET /api/v1/subscription/usage`
- `GET /api/v1/admin/businesses/{id}/subscription/usage`
- public commerce ingestion, once it reaches `SaleService.createSaleFromImport`. That method calls `validateSubscriptionForSale` → `getPlanForBusiness` before it writes the sale. A past-end plan becomes an empty `Optional`, the monthly quota check is skipped, and the `web` sale is still created. The webhook is not stopped by the 402 filter.

`GET /plans` does not flip the row.

### Nightly job

`findExpiredTrialsAndSubscriptions` selects `trialing` / `active` rows whose end is before today and whose cancel flag is false or null. For each row the job calls `expire()` and then `syncBusinessStatusOnExpiration(..., sub.isTrialing())`.

`isTrialing()` is already false, so the log line `wasTrialing=` is always `false`, and `trialUsedAt` is not set. The business **is** set to `expired` when its status is still `trial` or `active`.

`findCancelledAtPeriodEnd` selects the same statuses with the flag true and an end before today, then `markCancelled()`. It does not read or write the business. A trial cancelled at period end can stay `business.status=trial` with `trialUsedAt` null, which means `hasUsedTrial()` is still false.

`changePlan`, admin assign, and admin renew are the writers that set `trialUsedAt`. A pure expiry does not. Nothing in the tenant HTTP API calls `createTrialForNewBusiness` a second time; a later direct call on that same business id would be allowed.

## Pitfalls

| What you see | Why |
| --- | --- |
| 402 while `GET /subscription/plans` still works | Filter exemption is prefix `/api/v1/subscription` only. |
| 422 `Already on this plan and billing cycle` on the trial's last day, or the day after | Trial is `pro` / `monthly`, and `changePlan` does not look at the end date. `GET /me`, login, or refresh flips the status first; the same body then inserts a new period. |
| 402 text says the trial ended after a paid cancel | The message is constant. |
| `business.status` is `active` or `trial` and the API is still 402 | Access is the subscription row. Suspend, lazy cancel, and cancel-at-period-end do not all update the business. |
| Job log `wasTrialing=false` for a trial | `expire()` runs before the flag is read. `business.status` can still become `expired` on that path. |
| Second trial not blocked after the end date | `trial_used_at` stays null unless `changePlan` or an admin assign/renew ran. |
| Downgrade to starter succeeds with 3 stores | Tenant `changePlan` does not apply the store check that admin `assignPlan` applies. The next store create does. |
| `GET /me` is 204 immediately after `atPeriodEnd: false` | Immediate cancel drops the row out of `ACCESS_GRANTING`. Reactivate will not put it back. |
| Commerce webhook still opens a sale after the end date | `/api/v1/public/` skips the filter. `createSaleFromImport` lazy-expires, then treats "no plan" as "no quota". |
| Reactivate returns `No subscription to reactivate` the morning after the end date | The job's cancel path clears `cancelAtPeriodEnd`. |
| Plan edit invisible for up to 10 minutes | `plans` cache, no evict. |
| No charge and no invoice | Expected on this branch. |

## Codepaths

- `tenant/infrastructure/web/SubscriptionController.java`
- `tenant/application/service/SubscriptionService.java` (`createTrialForNewBusiness`, `changePlan`, `cancelSubscription`, `reactivateSubscription`, `notExpiredOrExpireLazy`)
- `tenant/application/job/SubscriptionExpirationJob.java`
- `tenant/infrastructure/security/SubscriptionRequiredFilter.java`
- `tenant/domain/model/Subscription.java`, `SubscriptionStatus.java`, `Business.java` (`activate`, `hasUsedTrial`)
- `tenant/domain/repository/SubscriptionRepository.java`
- `identity/application/service/AuthService.java` (trial on demo approval; plan slug on login and refresh)
- `admin/application/service/AdminBusinessService.java` (`assignPlan` store check, trial vs paid create)
- `shared/infrastructure/cache/CacheConfig.java`, `CachedLookups.activePlans`
- `db/migration/V45__subscription_lifecycle.sql`, `V46__business_trial_used_once.sql`, `V54__business_roles_permissions.sql`
