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

## API Endpoints

### Public
- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/demo-request` - Submit a demo signup for admin approval
- `POST /api/v1/auth/refresh` - Refresh access token
- `POST /api/v1/auth/forgot-password` - Request password reset
- `POST /api/v1/auth/reset-password` - Reset password from email token

### Protected (require JWT)
- `GET/POST/PUT/DELETE /api/v1/products` - Product management
- `GET/POST/PUT/DELETE /api/v1/stores` - Store management
- More endpoints for clients, suppliers, sales, etc.

### Platform Admin
- `GET /api/v1/admin/demo-requests` - List demo requests, optionally filtered by `status`
- `POST /api/v1/admin/demo-requests/{id}/approve` - Create the tenant and trial for a pending request
- `POST /api/v1/admin/demo-requests/{id}/reject` - Reject a pending request with an optional reason

## Project Structure

```
src/main/java/com/ecom360/
├── admin/            # Platform-admin APIs and reporting
├── analytics/        # Dashboard metrics
├── audit/            # Audit log domain and APIs
├── catalog/          # Products and categories
├── client/           # Client and client payment records
├── delivery/         # Couriers and deliveries
├── expense/          # Expense tracking
├── identity/         # Auth, JWT, users, demo requests
├── integration/      # API keys, webhooks, commerce integrations
├── inventory/        # Store stock and stock movements
├── notification/     # Notification preferences
├── sales/            # Sales and sale lines
├── shared/           # Cross-cutting exceptions, mail, web, config
├── store/            # Store management
├── supplier/         # Suppliers and purchase orders
└── tenant/           # Businesses, subscriptions, roles, members
```

Most modules follow the same package shape:

```
<module>/
├── application/      # Services and request/response DTOs
├── domain/           # JPA entities and repositories
└── infrastructure/   # REST controllers, security, adapters
```

## Multi-Tenancy

Most tenant data is scoped by `business_id`. Users belong to one or more businesses via
`business_user` and receive permissions through business roles. Default role codes are
normalized in uppercase French labels such as:

- `PROPRIETAIRE` - Owner
- `GESTIONNAIRE` - Manager
- `CAISSIER` - Cashier

Platform-admin routes under `/api/v1/admin/**` require a JWT with the `PLATFORM_ADMIN`
role and are not tied to one tenant.

## Demo Request Workflow

Demo access is intentionally not self-serve. A prospect submits a request, a platform
admin reviews it, and approval provisions the tenant.

### Submit a request

`POST /api/v1/auth/demo-request` is public and returns `202 Accepted` when the request is
stored.

```json
{
  "fullName": "Awa Diop",
  "email": "awa@example.com",
  "phone": "+221771234567",
  "businessName": "Boutique Awa",
  "message": "Nous voulons tester la gestion de stock multi-boutiques.",
  "jobTitle": "Gerante",
  "city": "Dakar",
  "sector": "Retail"
}
```

Constraints enforced by validation and service rules:

- `fullName`, `email`, `phone`, and `businessName` are required.
- `phone` must be 8-50 characters.
- `message` and rejection reasons are capped at 2000 characters.
- `jobTitle`, `city`, and `sector` are optional and capped at 128 characters.
- The email is trimmed and lowercased.
- A request is rejected if the email already belongs to a user, a business, or another
  pending demo request.

### Review as platform admin

List pending requests:

```bash
curl -H "Authorization: Bearer $PLATFORM_ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/admin/demo-requests?status=pending&page=0&size=20"
```

The API sorts newest first and caps page size at 100. Status values are stored as
`pending`, `approved`, and `rejected`.

Approve a request:

```bash
curl -X POST -H "Authorization: Bearer $PLATFORM_ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/admin/demo-requests/$REQUEST_ID/approve"
```

Approval creates:

- a user from the request contact details;
- a business using the requested business name and email;
- default business roles, assigning the requester `PROPRIETAIRE`;
- a trial subscription for the new business;
- audit log events for approval and tenant registration.

If the request does not already contain a password hash, the service creates a password
reset token and emails an invitation link built from `APP_URL`.

Reject a request:

```bash
curl -X POST -H "Authorization: Bearer $PLATFORM_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Outside current onboarding criteria"}' \
  "http://localhost:8080/api/v1/admin/demo-requests/$REQUEST_ID/reject"
```

Only `pending` requests can be approved or rejected. Rejection stores the reviewer,
timestamp, and optional reason, then attempts to notify the prospect by email.

### Operational notes

- Demo requests are stored in `demo_request` via Flyway migrations `V60` and `V61`.
- A partial unique index allows only one pending request per email, case-insensitively.
- Transactional email is optional in local/dev environments. If SMTP is unavailable,
  `EmailService` logs the message body instead of blocking the workflow.
- Set `APP_URL` to the frontend origin that should receive `/reset-password?token=...`
  links.
- Admin endpoints are protected both by the `/api/v1/admin/**` security rule and by
  service-level `isPlatformAdmin()` checks.

## License

Proprietary - 360 PME Commerce
