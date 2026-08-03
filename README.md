# 360 PME Commerce - Backend API

Backend API for the 360 PME Commerce SaaS platform - a multi-tenant POS and inventory management system.

## Tech Stack

- **Java 17**
- **Spring Boot 3.4**
- **Gradle 8.x**
- **PostgreSQL**
- **Flyway** (database migrations)
- **JWT** (authentication)
- **OpenAPI 3 / Swagger UI**

## Prerequisites

- JDK 17+
- PostgreSQL 14+
- Gradle 8.x (or use `./gradlew` wrapper)

## Quick Start

### 1. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE ecom360;
```

### 2. Configuration

Copy `.env.example` to `.env`, or create `application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecom360
    username: your_user
    password: your_password

jwt:
  secret: your-256-bit-secret-key-minimum-32-characters-long-for-production
```

### 3. Run

```bash
./gradlew bootRun
```

Or with Docker Compose (Postgres + API + Maildev):

```bash
docker compose up -d
```

### 4. Format & Quality

```bash
./gradlew spotlessApply   # format code
./gradlew spotlessCheck   # check formatting
./gradlew qualityGate     # format + compile + test + coverage
```

### 5. API Documentation

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api-docs

## Engineering docs

| Guide | Topic |
|-------|--------|
| [docs/sales-workflow.md](docs/sales-workflow.md) | POS create/update/void, stock, payment pitfalls |
| [docs/permissions.md](docs/permissions.md) | French role codes, `RolePermissionService`, navigation |
| [docs/integrations/commerce-webhooks.md](docs/integrations/commerce-webhooks.md) | Public HMAC webhooks, WooCommerce, idempotency |
| [docs/deployment/runbook.md](docs/deployment/runbook.md) | Docker, Flyway prod-off, upload dirs, healthchecks |

## API surface (high level)

### Public (no JWT)

- `POST /api/v1/auth/login`, `/refresh`, `/forgot-password`, `/reset-password`
- `POST /api/v1/auth/demo-request`
- `POST /api/v1/public/commerce/webhooks/incoming/{token}` (+ `/woocommerce`)
- Public file routes under `/api/v1/public/**` (product images, business logos)

### Protected (JWT + business context)

- `/api/v1/sales`, `/products`, `/stores`, `/clients`, `/stock`, `/expenses`, …
- `/api/v1/dashboard`, `/dashboard/global`, …
- `/api/v1/commerce/connections`, `/webhooks`, `/api-keys`, …

### Platform admin

- `/api/v1/admin/**` — requires `ROLE_PLATFORM_ADMIN`

## Project structure

Code is organized by **bounded context** (not a flat `controller/` / `service/` layout):

```
src/main/java/com/ecom360/
├── admin/          # Platform-admin APIs
├── analytics/      # Dashboard / global view
├── audit/          # Audit trail
├── catalog/        # Products, categories, product images
├── client/         # Customers / credit balances
├── delivery/       # Couriers & livraisons
├── expense/        # Business expenses
├── identity/       # Auth, roles, permissions, JWT security
├── integration/    # API keys, outbound webhooks, commerce ingestion
├── inventory/      # Stock levels & movements
├── notification/   # In-app notifications
├── platform/       # Platform-level domain
├── sales/          # POS sales & sale lines
├── shared/         # Cross-cutting config, mail, cache, exceptions
├── store/          # Stores
├── supplier/       # Suppliers & purchase orders
└── tenant/         # Business, plans, subscriptions, logos
```

Each context typically follows `application` / `domain` / `infrastructure` layers.

## Multi-tenancy & roles

All tenant data is scoped by `business_id`. Membership is via `business_user` with a `business_role`.

Canonical **French role codes** (uppercase):

- `PROPRIETAIRE` — owner (all permissions)
- `GESTIONNAIRE` — manager (broad; no subscription update / owner delete)
- `CAISSIER` — cashier (sales + limited reads)

See [docs/permissions.md](docs/permissions.md).

## License

Proprietary - 360 PME Commerce
