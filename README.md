# 360 PME Commerce - Backend API

Backend API for the 360 PME Commerce SaaS platform: multi-tenant POS, inventory,
sales, subscription, delivery, and commerce integration workflows.

## Tech stack

- Java 17
- Spring Boot 3.4
- Gradle 8.x wrapper
- PostgreSQL
- Flyway migrations
- JWT authentication
- OpenAPI 3 / Swagger UI
- Caffeine cache

## Quick start

### Option A: Docker Compose stack

Starts PostgreSQL, the API, and Maildev for local email capture.

```bash
docker compose up -d
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api-docs
- Maildev UI: http://localhost:1080

### Option B: Local JVM with PostgreSQL

Prerequisites:

- JDK 17+
- PostgreSQL 14+
- Gradle wrapper from this repository (`./gradlew`)

Create a local database:

```sql
CREATE DATABASE ecom360;
```

Run with local defaults:

```bash
./gradlew bootRun
```

Or override connection settings:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ecom360 \
DB_USERNAME=postgres \
DB_PASSWORD=postgres \
JWT_SECRET=dev-only-secret-key-min-32-characters-long \
./gradlew bootRun
```

## Documentation

- [Development setup and operations](docs/DEVELOPMENT.md)
- [Authentication and onboarding](docs/AUTH_AND_ONBOARDING.md)
- [Permissions and RBAC](docs/PERMISSIONS_AND_RBAC.md)
- [Commerce integrations](docs/COMMERCE_INTEGRATIONS.md)
- [Subscriptions and plans](docs/SUBSCRIPTIONS_AND_PLANS.md)
- [Delivery and team workflows](docs/DELIVERY_AND_TEAM.md)

Swagger is the canonical endpoint reference. The Markdown docs explain intent,
cross-service behavior, constraints, and common pitfalls that are not obvious
from request/response schemas alone.

## API surface

All application routes are under `/api/v1`.

### Public

- `POST /auth/login` - login with email and password.
- `POST /auth/demo-request` - request demo access; tenant provisioning happens
  only after platform-admin approval.
- `POST /auth/refresh` - refresh access token.
- `POST /auth/forgot-password` and `POST /auth/reset-password` - password reset.
- `/public/**` - public assets and signed commerce webhooks.
- `/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` - OpenAPI docs.
- `/actuator/health`, `/actuator/health/**`, `/actuator/info` - health checks.

### Protected tenant modules

JWT-authenticated business users can access tenant-scoped modules according to
their role permissions and plan features:

- Catalog: `/products`, `/categories`
- Inventory: `/stock`
- Sales: `/sales`
- Clients and suppliers: `/clients`, `/suppliers`, `/purchase-orders`
- Stores and business profile: `/stores`, `/business`
- Team and roles: `/business/users`, `/roles`, `/permissions`
- Subscription: `/subscription`
- Analytics: `/dashboard`
- Expenses: `/expenses`
- Notifications: `/notifications`
- API integrations: `/api-keys`, `/webhooks`, `/commerce/connections`
- Delivery: `/delivery/couriers`, `/delivery/deliveries`
- Audit: `/audit-logs`

Platform administration routes are under `/admin/**` and require the
`PLATFORM_ADMIN` authority.

## Architecture

The codebase is organized by bounded context under `src/main/java/com/ecom360`.
Most modules follow `domain/model`, `domain/repository`, `application/service`,
`application/dto`, and `infrastructure/web` packages.

| Module | Responsibility |
| --- | --- |
| `admin` | Cross-tenant platform administration, stats, demo request moderation |
| `analytics` | Dashboard, global view, reports-oriented aggregates |
| `audit` | Tenant and admin audit log APIs |
| `catalog` | Products and categories |
| `client` | Clients and client payments |
| `delivery` | Couriers (`livreur`) and delivery records (`livraison`) |
| `expense` | Expense categories and expenses |
| `identity` | Auth, users, JWT, permissions, demo requests |
| `integration` | API keys and outbound webhooks |
| `integration.commerce` | Inbound commerce order webhooks and WooCommerce mapping |
| `inventory` | Stock levels and movements |
| `notification` | Notifications and notification preferences |
| `sales` | POS sales and imported commerce sales |
| `shared` | Shared config, exceptions, mail, constants, web filters |
| `store` | Stores |
| `supplier` | Suppliers and purchase orders |
| `tenant` | Business profile, business users, roles, plans, subscriptions |

## Multi-tenancy, roles, and plans

- Tenant data is scoped by `business_id`.
- Business users are linked through `business_user` and a tenant-local
  `business_role`.
- System business role codes are `PROPRIETAIRE`, `GESTIONNAIRE`, and `CAISSIER`.
  Custom roles can be created through `/api/v1/roles`.
- Effective permissions and frontend navigation rules are returned by
  `/api/v1/permissions/me` and `/api/v1/me/permissions`.
- Subscriptions with status `trialing` or `active` grant access until
  `current_period_end`. Expired access returns HTTP 402 on tenant routes while
  leaving `/subscription/**`, `/admin/**`, and `/public/**` reachable.

## Development commands

```bash
./gradlew spotlessApply   # format Java code
./gradlew spotlessCheck   # check formatting
./gradlew test            # run tests
./gradlew qualityGate     # format check + compile + test + coverage
./gradlew flywayMigrate   # apply migrations to configured DB
./gradlew flywayReload    # dev reset: clean + migrate
```

Flyway migrations live in `src/main/resources/db/migration`. Treat Flyway as the
schema source of truth; Hibernate `ddl-auto: update` is a development
convenience, not a migration workflow.

## License

Proprietary - 360 PME Commerce
