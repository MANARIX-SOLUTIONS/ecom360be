# Development setup and operations

This backend is a Spring Boot API with PostgreSQL, Flyway, JWT auth, Maildev for
local email capture, and OpenAPI docs. The main configuration files are:

- `build.gradle.kts`
- `docker-compose.yml`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/application-test.yml`

## Local stack

The easiest local path is Docker Compose:

```bash
docker compose up -d
```

Services:

| Service | URL / port | Purpose |
| --- | --- | --- |
| API | `http://localhost:8080` | Spring Boot backend |
| PostgreSQL | `localhost:5432` | Local database named `ecom360` |
| Maildev | `http://localhost:1080` | Captures outgoing emails from demo requests, invites, and password resets |

The Compose API container uses `SPRING_PROFILES_ACTIVE=dev`, database host
`postgres`, and Maildev SMTP at `maildev:1025`.

To run the JVM locally against a local database:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ecom360 \
DB_USERNAME=postgres \
DB_PASSWORD=postgres \
JWT_SECRET=dev-only-secret-key-min-32-characters-long \
./gradlew bootRun
```

## Profiles

| Profile | Intended use | Notes |
| --- | --- | --- |
| `dev` | Local development | Swagger enabled, verbose SQL/security logs, Flyway clean allowed |
| `test` | CI/tests | Swagger disabled, random server port, cache disabled |
| `prod` | Production | Swagger disabled, structured logging, Flyway auto-migrate disabled |

Production expects secrets and CORS origins from environment variables. Do not
commit environment-specific secret values.

## Important environment variables

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev`, `test`, or `prod` |
| `SPRING_DATASOURCE_URL` | Full JDBC URL; preferred over assembling host/port/name |
| `DB_USERNAME`, `DB_PASSWORD` | Database credentials |
| `JWT_SECRET` | JWT signing secret; must be set outside dev/test |
| `APP_URL` | Frontend URL used in email links |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | SMTP settings |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins |
| `SUBSCRIPTION_EXPIRATION_CRON` | Cron expression for subscription expiration job |
| `BUSINESS_LOGOS_DIR` | Filesystem directory for uploaded business logos |

## Database migrations

Flyway migrations live in `src/main/resources/db/migration`.

Use sequential versioned files:

```text
V62__short_description.sql
```

There is one legacy out-of-order migration (`V8.1`), so Flyway is configured
with `out-of-order: true`. Continue using new integer versions for new work.

Common commands:

```bash
./gradlew flywayMigrate
./gradlew flywayReload
```

`flywayReload` runs clean + migrate and is for development databases only.
Flyway is the schema source of truth. Hibernate `ddl-auto: update` is present as
a development convenience and should not replace migrations.

## Quality commands

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew test
./gradlew qualityGate
```

`qualityGate` runs format checks, Java compilation, tests, JaCoCo report
generation, and coverage verification.

## API docs and health checks

In `dev`:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Actuator health: `http://localhost:8080/actuator/health`

Swagger is disabled in `prod` by `application-prod.yml`.

## Troubleshooting

### The API starts but demo/invite emails are missing

Check Maildev at `http://localhost:1080`. If running outside Compose, set:

```bash
MAIL_HOST=localhost
MAIL_PORT=1025
```

### Tenant routes return HTTP 402

The business has no current access-granting subscription (`trialing` or
`active`) through `current_period_end`. Use `/api/v1/subscription/plans` and
`/api/v1/subscription/change` to recover access. See
[Subscriptions and plans](SUBSCRIPTIONS_AND_PLANS.md).

### Swagger is not available

Verify the active profile. Swagger is enabled in `dev` and disabled in `prod`.

### Migrations fail locally after branch changes

For disposable local databases, run:

```bash
./gradlew flywayReload
```

Do not run clean/reload against shared or production databases.
