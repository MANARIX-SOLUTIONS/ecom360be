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
- Backend behavior guide: [docs/backend-domain-behavior.md](docs/backend-domain-behavior.md)

The backend behavior guide documents tenant access, subscriptions, roles,
inventory, delivery, commerce webhooks, demo approvals, and operational notes
that are not obvious from endpoint signatures alone.

## API Endpoints

### Public
- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/demo-request` - Request demo access before tenant creation
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/forgot-password` - Request password reset
- `POST /api/v1/auth/reset-password` - Reset password
- `POST /api/v1/public/commerce/webhooks/incoming/{token}` - Commerce order webhook
- `POST /api/v1/public/commerce/webhooks/incoming/{token}/woocommerce` - WooCommerce order webhook

### Protected (require JWT)
- `/api/v1/products`, `/api/v1/categories`, `/api/v1/stores` - Catalog and store management
- `/api/v1/stock` - Stock initialization, adjustments, and movements
- `/api/v1/clients`, `/api/v1/suppliers`, `/api/v1/purchase-orders` - CRM and supplier workflows
- `/api/v1/sales` - Sales and stock deduction
- `/api/v1/delivery/couriers`, `/api/v1/delivery/deliveries` - Delivery tracking
- `/api/v1/subscription` - Plans, usage, changes, cancellation, and reactivation
- `/api/v1/business/users`, `/api/v1/roles` - Tenant team and permission management
- `/api/v1/api-keys`, `/api/v1/webhooks`, `/api/v1/commerce/connections` - Integrations
- `/api/v1/admin/**` - Platform administration, restricted to platform admins

## Project Structure

```
src/main/java/com/ecom360/
├── admin/            # Platform administration endpoints and services
├── analytics/        # Dashboard and report data
├── audit/            # Audit logging
├── catalog/          # Products and categories
├── client/           # Clients and client payments
├── delivery/         # Couriers and delivery/livraison tracking
├── expense/          # Expenses and categories
├── identity/         # Auth, JWT, permissions, demo requests
├── integration/      # API keys, webhooks, commerce ingestion
├── inventory/        # Stock levels and movements
├── notification/     # Notifications and preferences
├── sales/            # Sales and sale lines
├── shared/           # Shared web, config, exceptions, mail
├── store/            # Stores
├── supplier/         # Suppliers and purchase orders
└── tenant/           # Businesses, subscriptions, roles, users
```

## Multi-Tenancy

All data is scoped by `business_id`. Users belong to one or more businesses via `business_user` with roles:
- `PROPRIETAIRE` - Owner, all permissions
- `GESTIONNAIRE` - Manager, broad permissions without subscription updates or user deletion
- `CAISSIER` - Cashier, limited read and sales permissions

Expired tenants are blocked from tenant resources with HTTP `402` until they
recover subscription access. Subscription, admin, and public paths remain
available so recovery and public integrations can continue.

## License

Proprietary - 360 PME Commerce
