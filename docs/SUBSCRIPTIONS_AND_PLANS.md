# Subscriptions and plans

Subscriptions control tenant access and plan feature gates. New businesses get a
trial during demo approval; paid plans can then be selected through the
subscription API.

Primary codepaths:

- `tenant/application/service/SubscriptionService.java`
- `tenant/application/service/SubscriptionUsageService.java`
- `tenant/application/job/SubscriptionExpirationJob.java`
- `tenant/infrastructure/security/SubscriptionRequiredFilter.java`
- `tenant/infrastructure/web/SubscriptionController.java`
- `tenant/domain/model/Plan.java`
- `tenant/domain/model/SubscriptionStatus.java`
- `src/main/resources/db/migration/V45__subscription_lifecycle.sql`
- `src/main/resources/db/migration/V46__business_trial_used_once.sql`
- `src/main/resources/db/migration/V8__update_plans_to_spec.sql`
- `src/main/resources/db/migration/V51__delivery_couriers_and_plan_feature.sql`
- `src/main/resources/db/migration/V53__plan_feature_global_view.sql`

## Subscription statuses

`SubscriptionStatus.ACCESS_GRANTING` contains:

- `trialing`
- `active`

Only these statuses grant tenant access, and only until
`current_period_end`.

Other known statuses:

- `expired`
- `past_due`
- `cancelled`
- `paused`
- `incomplete`

## Trial behavior

`SubscriptionService.createTrialForNewBusiness` creates a 14-day trial for new
businesses, using the `pro` plan when available. Trials are allowed once per
business; `business.trial_used_at` records usage when access is converted or a
trial expires.

Tenant provisioning from demo approval is the normal source of new trials. See
[Authentication and onboarding](AUTH_AND_ONBOARDING.md).

## Access enforcement

`SubscriptionRequiredFilter` runs after JWT authentication. For authenticated
business users, it checks for an access-granting subscription whose
`current_period_end` is today or later.

When no current access-granting subscription exists, tenant routes return:

```http
HTTP/1.1 402 Payment Required
Content-Type: application/json

{
  "code": "SUBSCRIPTION_REQUIRED",
  "message": "Votre période d'essai est terminée. Veuillez souscrire à un plan pour continuer."
}
```

Exempt paths:

- `/api/v1/subscription/**` so users can view plans, change plan, cancel, or
  reactivate.
- `/api/v1/admin/**` for platform administration.
- `/api/v1/public/**` for public assets and commerce webhooks.

Users without business access, including platform admins, are not blocked by
this filter.

## Subscription API

All routes require JWT and a business context.

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/subscription/usage` | `SUBSCRIPTION_READ` | Current usage vs plan limits |
| `GET` | `/api/v1/subscription/me` | `SUBSCRIPTION_READ` | Current subscription or 204 |
| `GET` | `/api/v1/subscription/plans` | `SUBSCRIPTION_READ` | Available active plans |
| `POST` | `/api/v1/subscription/change` | `SUBSCRIPTION_UPDATE` | Subscribe, upgrade, downgrade, or recover expired access |
| `POST` | `/api/v1/subscription/cancel` | `SUBSCRIPTION_UPDATE` | Cancel now or at period end |
| `POST` | `/api/v1/subscription/reactivate` | `SUBSCRIPTION_UPDATE` | Remove cancel-at-period-end before the period ends |

`POST /subscription/change` accepts `planSlug` and `billingCycle`. Billing cycle
defaults to monthly unless `yearly` is provided.

## Cancellation and expiration

Default cancellation is at period end. Immediate cancellation is supported by
passing `atPeriodEnd: false` to `/subscription/cancel`.

`SubscriptionExpirationJob` runs daily by default:

```text
0 0 2 * * ?
```

Override with `SUBSCRIPTION_EXPIRATION_CRON`.

The job:

1. marks expired trials/subscriptions as `expired`;
2. updates the business status to `expired`;
3. records `trial_used_at` when an expired subscription was a trial;
4. marks subscriptions with `cancel_at_period_end` as `cancelled` after period
   end.

Some service reads also lazy-expire current subscriptions when they detect an
ended period.

## Plan limits and feature flags

Plan limits use `0` to mean unlimited.

Current seed values:

| Plan | Monthly price | Users | Stores | Products | Sales/month | Clients | Suppliers |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `starter` | 15000 | 1 | 1 | 200 | 100 | 50 | 10 |
| `pro` | 35000 | 5 | 3 | 0 | 0 | 0 | 0 |
| `business` | 75000 | 0 | 0 | 0 | 0 | 0 | 0 |

Selected feature flags:

| Feature | Starter | Pro | Business | Used by |
| --- | --- | --- | --- | --- |
| `feature_reports` | No | Yes | Yes | Reports/analytics gates |
| `feature_role_management` | No | Yes | Yes | Non-cashier invites and role management constraints |
| `feature_api` | No | No | Yes | API keys, webhooks, commerce connections |
| `feature_delivery_couriers` | No | Yes | Yes | Delivery couriers and deliveries |
| `feature_global_view` | No | Yes | Yes | Global dashboard view |
| `feature_supplier_tracking` | No | Yes | Yes | Purchase order/supplier advanced tracking |

## Usage counters

`GET /api/v1/subscription/usage` returns current counts and limits for:

- active business users;
- stores;
- products;
- clients;
- suppliers;
- sales in the current month.

Platform admins can compute usage for any business through admin business
routes.

## Common pitfalls

- A valid JWT is not enough for tenant routes; the business also needs a current
  `trialing` or `active` subscription.
- If a user is locked out by HTTP 402, use `/subscription/plans` and
  `/subscription/change`; those routes are intentionally exempt.
- `cancel_at_period_end` keeps access until the period ends.
- Plan feature flags and permission checks are separate: a user may need both a
  plan feature and a permission code.
