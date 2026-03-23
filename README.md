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
- `POST /api/v1/auth/register` - Register user + business
- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/refresh` - Refresh access token

### Protected (require JWT)
- `GET/POST/PUT/DELETE /api/v1/products` - Product management
- `GET/POST/PUT/DELETE /api/v1/stores` - Store management
- More endpoints for clients, suppliers, sales, etc.

## Project Structure

```
src/main/java/com/ecom360/
├── common/           # Shared utilities, constants
├── config/           # Spring configuration
├── controller/       # REST controllers
├── dto/              # Request/Response DTOs
├── entity/           # JPA entities
├── exception/        # Exception handling
├── repository/       # JPA repositories
├── security/         # JWT, auth config
└── service/          # Business logic
```

## Multi-Tenancy

All data is scoped by `business_id`. Users belong to one or more businesses via `business_user` with roles:
- `proprietaire` - Owner
- `gestionnaire` - Manager
- `caissier` - Cashier

## License

Proprietary - 360 PME Commerce
