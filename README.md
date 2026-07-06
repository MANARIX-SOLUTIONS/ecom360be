# 360 PME Commerce - Backend API

Backend API for the 360 PME Commerce SaaS platform: a multi-tenant POS,
inventory, sales, subscription, and commerce-integration system.

## Tech Stack

- **Java 17**
- **Spring Boot 3.4**
- **Gradle 8.x** via `./gradlew`
- **PostgreSQL 16**
- **Flyway** database migrations
- **Spring Security + JWT**
- **OpenAPI 3 / Swagger UI**
- **Docker Compose** for local and production-style stacks

## Documentation

- [Architecture overview](docs/architecture.md)
- [Commerce webhook ingestion](docs/integrations/commerce-webhooks.md)
- [Permissions and navigation](docs/permissions.md)
- [Deployment and migration runbook](docs/deployment/runbook.md)

## Prerequisites

- JDK 17+
- Docker and Docker Compose for the recommended local stack
- PostgreSQL 14+ if running the database outside Docker

## Quick Start

### Option A: Docker Compose local stack

```bash
docker compose up -d
```

This starts:

- API: http://localhost:8080
- PostgreSQL: `localhost:5432`, database `ecom360`
- Maildev UI: http://localhost:1080

The API container runs with `SPRING_PROFILES_ACTIVE=dev`; Flyway migrations run
on startup.

### Option B: Run the API on the host

Start PostgreSQL first:

```bash
docker compose up -d postgres maildev
```

Copy the environment template and adjust values if needed:

```bash
cp .env.example .env
```

Run the API with defaults:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Or load `.env` values into the shell first:

```bash
set -a; . ./.env; set +a
./gradlew bootRun
```

Useful environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ecom360
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=dev-only-secret-key-min-32-characters-long-change-in-prod
MAIL_HOST=localhost
MAIL_PORT=1025
```

## Quality Checks

```bash
./gradlew spotlessApply   # format Java
./gradlew spotlessCheck   # check formatting
./gradlew test            # run tests
./gradlew qualityGate     # formatting + compile + tests + coverage
```

CI runs Spotless, Java compilation, tests, JaCoCo, and `bootJar` on pushes and
pull requests targeting `main` and `develop`.

## API Documentation

When Swagger is enabled:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api-docs

`application-prod.yml` disables Swagger by default. Use local or staging
environments for interactive API exploration.

## API Surface

All application endpoints use base path `/api/v1`.

### Public endpoints

- `POST /api/v1/auth/demo-request` - request demo access; creates a pending lead
  for platform-admin approval.
- `POST /api/v1/auth/login` - exchange credentials for access and refresh tokens.
- `POST /api/v1/auth/refresh` - refresh an access token.
- `POST /api/v1/auth/forgot-password` and `/reset-password` - password reset flow.
- `POST /api/v1/public/commerce/webhooks/incoming/{token}` - canonical commerce
  order webhook secured by URL token plus HMAC header.
- `POST /api/v1/public/commerce/webhooks/incoming/{token}/woocommerce` -
  WooCommerce order webhook with the same HMAC contract.

### Protected tenant endpoints

Tenant endpoints require `Authorization: Bearer <jwt>` and are scoped by the
business embedded in the JWT. Main resource areas include:

- Products, categories, stock, stores
- Clients, suppliers, purchase orders
- Sales, dashboard, reports, global view
- Expenses, couriers, deliveries
- Business profile, users, roles, subscription
- Notifications, audit logs
- API keys, outbound webhooks, commerce connections

### Platform admin endpoints

`/api/v1/admin/**` requires `ROLE_PLATFORM_ADMIN` and covers businesses, users,
stores, demo requests, platform stats, and admin audit workflows.

## Project Structure

The backend is organized by bounded context under `src/main/java/com/ecom360/`.
Most contexts follow `application/`, `domain/`, and `infrastructure/web/`
packages.

```text
admin/          # Platform admin workflows
analytics/      # Dashboard and global view
audit/          # Tenant audit logs
catalog/        # Products and categories
client/         # Clients and payments
delivery/       # Couriers and deliveries
expense/        # Expenses and categories
identity/       # Auth, JWT, users, permissions
integration/    # API keys, outbound webhooks, commerce ingestion
inventory/      # Stock levels and movements
notification/   # Notifications and preferences
sales/          # POS sales
shared/         # Cross-cutting config, exceptions, web constants
store/          # Multi-store management
supplier/       # Suppliers and purchase orders
tenant/         # Business profile, subscriptions, roles, scheduled jobs
```

## Multi-Tenancy and Access

Tenant data is scoped by `business_id`. Users are attached to businesses through
`business_user` and receive role-based permissions. System role codes are:

- `PROPRIETAIRE` - owner
- `GESTIONNAIRE` - manager
- `CAISSIER` - cashier

After authentication, `SubscriptionRequiredFilter` blocks most tenant endpoints
with HTTP 402 `SUBSCRIPTION_REQUIRED` when the business has no current access-
granting subscription. Subscription, admin, and public paths remain reachable.

## Operational Notes

- Flyway auto-runs in dev/test. Production disables automatic Flyway migration;
  run migrations separately before deployment.
- The in-process subscription expiration job runs daily at 02:00 by default and
  is configurable with `SUBSCRIPTION_EXPIRATION_CRON`.
- Docker images expose API port `8080` and management port `8081`. The prod
  Spring profile sets `server.port: 8090`, so production deployments should set
  `SERVER_PORT=8080` or adjust compose port mappings consistently.
- Business logo uploads are stored under `BUSINESS_LOGOS_DIR` (default in the
  image: `/app/data/uploads/business-logos`); mount it as persistent storage in
  production.

## License

Proprietary - 360 PME Commerce
