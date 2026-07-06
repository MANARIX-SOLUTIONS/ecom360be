# Backend Architecture

360 PME Commerce is a Spring Boot monolith organized as bounded contexts. The
runtime is one deployable JAR/container, but code ownership follows domain
boundaries so tenant, catalog, sales, integration, and admin rules stay close to
their data and workflows.

## Request Flow

```text
HTTP request
  -> SecurityConfig
  -> JwtAuthenticationFilter
  -> SubscriptionRequiredFilter
  -> context controller
  -> application service
  -> domain model/repository
  -> PostgreSQL
```

Key files:

- `shared/infrastructure/web/ApiConstants.java` defines `/api/v1` and common
  headers.
- `identity/infrastructure/security/SecurityConfig.java` defines public paths,
  admin authorization, CORS, JWT authentication, and subscription filtering.
- `tenant/infrastructure/security/SubscriptionRequiredFilter.java` enforces
  subscription access for tenant APIs.
- `shared/infrastructure/config/FlywayConfig.java` repairs then migrates in
  environments where Flyway is enabled.

## Bounded Contexts

| Context | Intent |
| --- | --- |
| `admin` | Platform-admin operations: businesses, users, stores, demo requests, stats. |
| `analytics` | Dashboard metrics, top products, low-stock lists, global view. |
| `audit` | Tenant audit log reads and request tracking. |
| `catalog` | Product and category lifecycle. |
| `client` | Client records and payments. |
| `delivery` | Couriers and delivery/livraison workflows. |
| `expense` | Expense categories and expense entries. |
| `identity` | Auth, JWT, users, permission catalogue, navigation permissions. |
| `integration` | API keys, outbound webhooks, commerce connections and ingestion. |
| `inventory` | Store stock, product stock, stock movements. |
| `notification` | Notifications and notification preferences. |
| `sales` | POS sale creation, updates, imports, and sale lines. |
| `shared` | Cross-cutting config, exceptions, mail, constants, common web behavior. |
| `store` | Store management. |
| `supplier` | Suppliers, purchase orders, supplier payments. |
| `tenant` | Business profile, roles, subscriptions, scheduled subscription lifecycle. |

Most contexts use:

```text
application/        # DTOs and business use cases
domain/             # JPA models, repositories, domain concepts
infrastructure/web/ # REST controllers and HTTP mapping
```

## Tenancy Model

Tenant APIs are scoped by the authenticated user's `businessId`, which is
resolved into `UserPrincipal` by JWT authentication. Services should prefer
repository methods that include `businessId` and should reject cross-business
resource access before mutating data.

The platform-admin surface is separate:

- `/api/v1/admin/**` requires `ROLE_PLATFORM_ADMIN`.
- Admin principals may not have a tenant `businessId`.
- Tenant subscription checks do not block admin routes.

## Authentication and Subscription Gate

Public routes include login, refresh, password reset, demo requests, public
assets, public commerce webhooks, Swagger when enabled, and health/info
actuator endpoints.

Protected tenant routes require:

```http
Authorization: Bearer <access-token>
```

After JWT authentication, `SubscriptionRequiredFilter` returns HTTP 402 with
error code `SUBSCRIPTION_REQUIRED` and a localized message when the authenticated
business has no access-granting subscription whose current period includes
today. The filter still allows:

```json
{
  "code": "SUBSCRIPTION_REQUIRED",
  "message": "<localized French subscription-required message>"
}
```

- `/api/v1/subscription/**`
- `/api/v1/admin/**`
- `/api/v1/public/**`

## Background Work

Scheduling is enabled by `@EnableScheduling` in `Ecom360Application`.

The current scheduled job is `SubscriptionExpirationJob`, which runs daily at
02:00 by default:

```properties
SUBSCRIPTION_EXPIRATION_CRON=0 0 2 * * ?
```

The job:

1. Finds expired trials/subscriptions.
2. Marks subscriptions expired.
3. Sets the business status to `expired` when the business was `trial` or
   `active`.
4. Records `trialUsedAt` when an expired subscription was trialing.
5. Marks cancel-at-period-end subscriptions cancelled.

Because scheduling is in-process, every running API instance can execute the
job. Add a distributed lock before scaling this job across multiple active
instances.

## Persistence and Migrations

- Migrations live in `src/main/resources/db/migration`.
- Dev/test enable Flyway on startup.
- Base config sets `spring.jpa.hibernate.ddl-auto=update`; treat this as a
  development safety net, not a production schema-management strategy.
- Production config disables Flyway auto-migration and disables Flyway clean.
  Run `./gradlew flywayMigrate` against the target database before deploying a
  production image.
- Flyway `out-of-order` is enabled to support the existing `V8.1` migration.

## API Contracts

Common headers:

| Header | Use |
| --- | --- |
| `Authorization` | `Bearer <jwt>` for protected tenant/admin APIs. |
| `X-Request-Id` | Exposed by CORS for request correlation. |
| `X-Commerce-Signature` | HMAC-SHA256 signature for public commerce webhooks. |

Swagger/OpenAPI is available at `/swagger-ui.html` and `/api-docs` when
`SWAGGER_ENABLED=true`. The prod profile disables both endpoints.
