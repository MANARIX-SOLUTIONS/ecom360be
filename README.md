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

Copy and configure environment variables or create `application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecom360
    username: your_user
    password: your_password

jwt:
  secret: your-256-bit-secret-key-minimum-32-characters-long-for-production
```

See `.env.example` for the full variable list used in Docker / prod.

### 3. Run

```bash
./gradlew bootRun
```

Or with custom DB config:

```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=ecom360 DB_USERNAME=postgres DB_PASSWORD=postgres ./gradlew bootRun
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
| [docs/suppliers-and-purchases.md](docs/suppliers-and-purchases.md) | Supplier CRUD, PO lifecycle, receive → stock/balance, payments |
| [docs/client-credits.md](docs/client-credits.md) | Credit sales via update, repayments, receivable aggregate |

## API Endpoints

### Public (no JWT)

- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/forgot-password` / `reset-password`
- `POST /api/v1/auth/demo-request` - Public demo / onboarding request
- Public file routes under `/api/v1/public/**` (product images, business logos)

### Protected (require JWT + business context)

- `GET/POST/PUT/DELETE /api/v1/products` - Product management
- `GET/POST/PUT/DELETE /api/v1/stores` - Store management
- `/api/v1/sales`, `/clients`, `/suppliers`, `/purchase-orders`, `/stock`, `/expenses`, …
- `/api/v1/dashboard`, `/dashboard/global`, …

## Project Structure

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

## Multi-Tenancy

All tenant data is scoped by `business_id`. Membership is via `business_user` with a `business_role`.

Canonical **French role codes** (uppercase):

- `PROPRIETAIRE` - Owner
- `GESTIONNAIRE` - Manager
- `CAISSIER` - Cashier

## License

Proprietary - 360 PME Commerce
