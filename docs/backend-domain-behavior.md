# Backend domain behavior guide

This guide captures backend behavior that is easy to miss when working only from
the OpenAPI UI. It is verified against the Spring controllers, services, config,
and Flyway migrations in this repository.

## API conventions

- The API base path is `/api/v1`.
- Most endpoints require a bearer JWT and a tenant business context from the
  authenticated principal.
- Public endpoints are limited to authentication helpers, `/api/v1/public/**`,
  selected actuator health/info endpoints, and Swagger when enabled.
- `X-Request-Id` is accepted on incoming requests. If it is missing, the backend
  generates a short ID, adds it to the response header, stores it in logging MDC,
  and logs method, URI, status, and duration for non-actuator requests.

## Subscriptions and tenant access

Subscriptions are managed under `/api/v1/subscription`.

| Endpoint | Purpose |
| --- | --- |
| `GET /usage` | Current usage versus plan limits. |
| `GET /me` | Current access-granting subscription, or `204` when none exists. |
| `GET /plans` | Active plans ordered by monthly price. |
| `POST /change` | Subscribe or switch plan and billing cycle. |
| `POST /cancel` | Cancel immediately or at period end. |
| `POST /reactivate` | Reactivate a cancel-at-period-end subscription before it ends. |

Important constraints:

- New demo-approved tenants receive one 14-day trial. Trial reuse for a business
  is rejected.
- Access-granting subscriptions are lazily expired when read and are also
  processed by `SubscriptionExpirationJob`.
- The expiration job runs with `app.subscription.expiration-cron`, defaulting to
  `0 0 2 * * ?`.
- When a tenant has no current access-granting subscription, tenant resources are
  blocked with HTTP `402` and code `SUBSCRIPTION_REQUIRED`.
- Subscription, admin, and `/api/v1/public/**` paths bypass the subscription
  block so users can recover access or public webhooks can still be received.

Plan feature gates are enforced in services, not only in controllers:

- API keys, outgoing webhook registrations, and commerce connections require
  the plan API feature.
- Delivery couriers and deliveries require the delivery couriers feature.
- Reports and supplier tracking have separate feature checks.
- User, store, product, client, supplier, sale, and history limits are enforced
  where those resources are created or queried.

## Roles, permissions, and business users

Default tenant roles are bootstrapped per business:

- `PROPRIETAIRE`: all permissions.
- `GESTIONNAIRE`: all permissions except subscription updates and business-user
  deletion.
- `CAISSIER`: narrow read/sales permissions, including product/category/stock/
  client/store reads, sale create/read/delete, and reports read.

Legacy role aliases are normalized:

| Input | Canonical role |
| --- | --- |
| `PROPRIETAIRE`, `PROPRIETAIRE` with accent, `ADMIN` | `PROPRIETAIRE` |
| `GESTIONNAIRE`, `MANAGER` | `GESTIONNAIRE` |
| `CAISSIER`, `SELLER`, blank/null | `CAISSIER` |

Business user management lives under `/api/v1/business/users`:

- `GET /api/v1/business/users` lists active members.
- `POST /api/v1/business/users` invites a user. New users receive a password
  reset/invitation link.
- `PUT /api/v1/business/users/{id}/stores` replaces store assignments for a
  member.
- `GET /api/v1/business/users/{id}/stores` returns assigned stores.

Invite constraints:

- The active-user count cannot exceed the plan max users unless the limit is
  configured as unlimited.
- If role management is not enabled for the plan, invites are limited to
  `CAISSIER`.
- A user cannot be invited twice to the same business.

Custom roles are managed under `/api/v1/roles`:

- `GET /api/v1/roles`
- `POST /api/v1/roles`
- `POST /api/v1/roles/{id}/permissions`

## Inventory stock

Stock endpoints are under `/api/v1/stock`.

| Endpoint | Purpose |
| --- | --- |
| `POST /init` | Create stock for a product/store pair. |
| `POST /adjust` | Record a manual stock movement. |
| `GET /store/{storeId}` | List stock levels by store. |
| `GET /product/{productId}/store/{storeId}` | Read one stock level. |
| `GET /movements/product/{productId}/store/{storeId}` | Page movements for one product/store. |
| `GET /movements/store/{storeId}` | Page all movements for a store. |

Rules:

- Stock is unique by `(product_id, store_id)`.
- `POST /init` fails if the product/store stock already exists.
- A positive initial quantity records an `in` movement with note
  `Initial stock`.
- Manual adjustment types are:
  - `in`: positive delta, stored as an absolute positive quantity.
  - `out`: negative delta, stored as an absolute negative quantity.
  - `adjustment`: sets the final absolute quantity; negative final quantities
    are rejected.
- Stock cannot go below zero.
- Sale creation records a `sale` stock movement and also cannot take stock below
  zero.
- Low stock is reported when `quantity <= minStock`.

Example manual adjustment:

```json
{
  "productId": "00000000-0000-0000-0000-000000000001",
  "storeId": "00000000-0000-0000-0000-000000000002",
  "type": "out",
  "quantity": 3,
  "reference": "manual-count-2026-07",
  "note": "Damaged items removed"
}
```

## Delivery couriers and deliveries

Delivery uses API English paths and French database table names:

- API: `/api/v1/delivery/couriers`
- API: `/api/v1/delivery/deliveries`
- Tables: `livreur`, `livraison`

The delivery couriers feature is enabled for `pro` and `business` plans and
disabled for `starter` in migration `V51__delivery_couriers_and_plan_feature.sql`.

Courier endpoints:

- `GET /api/v1/delivery/couriers?activeOnly=true|false`
- `GET /api/v1/delivery/couriers/stats`
- `GET /api/v1/delivery/couriers/{id}`
- `GET /api/v1/delivery/couriers/{id}/stats`
- `POST /api/v1/delivery/couriers`
- `PUT /api/v1/delivery/couriers/{id}`
- `DELETE /api/v1/delivery/couriers/{id}`

Delivery endpoints:

- `POST /api/v1/delivery/deliveries`
- `GET /api/v1/delivery/deliveries`
- `GET /api/v1/delivery/deliveries?courierId={courierId}`

Delivery statuses are `delivered`, `failed`, and `cancelled`. Delivered records
receive `deliveredAt=now`; failed and cancelled records do not. Courier success
rate is calculated from delivered and failed counts only, so cancelled deliveries
do not reduce the success rate. A delivery `parcelsCount` less than 1 is coerced
to 1 by the request DTO and is constrained in the database.

## API keys, webhooks, and commerce ingestion

API keys are managed under `/api/v1/api-keys` and require the plan API feature.
The raw key is returned only once at creation, with prefix `ecom360_`; later
responses omit it.

Outgoing webhook registrations are managed under `/api/v1/webhooks` and also
require the plan API feature. The secret is returned only once at creation. This
service currently manages registrations; no dispatcher is present in the backend
codepaths covered by this guide.

Commerce connections are managed under `/api/v1/commerce/connections`.
Creating a connection stores a generated incoming token and HMAC secret. The
response includes:

- `incomingWebhookUrl`: `/api/v1/public/commerce/webhooks/incoming/{token}`
- `hmacSecret`: returned only in the create response

Public commerce ingestion endpoints do not require JWT, but they require the
`X-Commerce-Signature` header:

- `POST /api/v1/public/commerce/webhooks/incoming/{token}`
- `POST /api/v1/public/commerce/webhooks/incoming/{token}/woocommerce`

Signature rules:

- HMAC-SHA256 over the raw UTF-8 request body.
- The shared secret is the connection `hmacSecret`.
- Accepted header forms are raw hex, `sha256=<hex>`, or `v1=<hex>`.
- Invalid or missing signatures are rejected before payload processing.

Example signature generation:

```bash
BODY='{"sourceType":"GENERIC_WEBHOOK","externalOrderId":"web-1001","currency":"XOF","paymentStatus":"paid","lines":[{"sku":"SKU-001","label":"Product","quantity":1,"unitPriceMinorUnits":1000}]}'
SIGNATURE=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$HMAC_SECRET" -hex | awk '{print $2}')
curl -X POST "$API_URL/api/v1/public/commerce/webhooks/incoming/$TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Commerce-Signature: sha256=$SIGNATURE" \
  --data "$BODY"
```

Ingestion constraints:

- The canonical payload requires `sourceType`, `externalOrderId`, `currency`,
  `paymentStatus`, and at least one line.
- Each line must resolve to an active product in the connection store by
  `productId` or `sku`.
- Eligible payment statuses are `paid`, `completed`, `captured`, `settled`,
  `paid_in_full`, and `processing`.
- The WooCommerce endpoint requires a connection with `sourceType=WOOCOMMERCE`.
- Generic connections with `sourceType=GENERIC_WEBHOOK` accept any payload
  source type; other connection types must match the payload `sourceType`.
- Successful ingestion creates a sale with payment method `web` and returns
  HTTP `202`.
- Re-sending a successfully processed external order for the same connection is
  idempotent and returns HTTP `200` with status `DUPLICATE_SKIPPED`.
- Stored raw payloads are truncated to 12,000 characters in ingestion logs.

## Demo request approval flow

Public demo signup:

- `POST /api/v1/auth/demo-request`
- No JWT required.
- Returns HTTP `202` with an acknowledgement message when accepted.

Required request fields are `fullName`, `email`, `phone`, and `businessName`.
Optional lead fields are `message`, `jobTitle`, `city`, and `sector`.

Submission is rejected when:

- A user already exists with the email.
- A business already exists with the email.
- A pending demo request already exists for the email.

Platform admins manage requests under `/api/v1/admin/demo-requests`:

- `GET /api/v1/admin/demo-requests?status=pending|approved|rejected`
- `POST /api/v1/admin/demo-requests/{id}/approve`
- `POST /api/v1/admin/demo-requests/{id}/reject`

Approval creates the user, business, default roles, owner membership, and trial.
When the demo request has no stored password hash, the approval flow creates a
password reset token and sends an invitation link. Rejection stores the reason
and attempts to notify the requester by email.

## Runtime and operations notes

- The default active profile is `dev` unless `SPRING_PROFILES_ACTIVE` is set.
- Local/default server port is `8080`; the prod profile uses app port `8090`.
- Prod actuator runs on management port `8081`.
- Swagger UI is enabled in dev and disabled in prod.
- Mail is optional. When no SMTP host is configured, email behavior depends on
  the active mail configuration and service implementation.
- Base Flyway config uses `classpath:db/migration`, validates on migrate, and
  allows out-of-order migrations.
- `FlywayConfig` runs `repair()` before `migrate()` when Flyway auto-migration
  is enabled.
- The prod profile disables Flyway auto-migration and disables Flyway clean.
  Run migrations separately before deploying prod.
- The base JPA setting is `spring.jpa.hibernate.ddl-auto=update`; the prod
  profile does not override it in the current config. Treat schema management
  changes as operationally sensitive.
- Prod logs are structured JSON to stdout and a rolling file, with `requestId`
  included from MDC.

## Source codepaths covered

- `shared/infrastructure/web/ApiConstants.java`
- `identity/infrastructure/security/SecurityConfig.java`
- `shared/infrastructure/web/RequestLoggingFilter.java`
- `tenant/**` subscription, roles, business users, and subscription filter/job
- `inventory/**` stock levels and movements
- `delivery/**` couriers and deliveries
- `integration/**` API keys, outgoing webhook registrations, and commerce
  ingestion
- `identity/**` and `admin/**` demo request submission and review
- `src/main/resources/application*.yml`
- `src/main/resources/db/migration/V51__delivery_couriers_and_plan_feature.sql`
- `src/main/resources/db/migration/V52__delivery_livraison.sql`
- `src/main/resources/db/migration/V60__demo_request.sql`
- `src/main/resources/db/migration/V61__demo_request_lead_fields.sql`
