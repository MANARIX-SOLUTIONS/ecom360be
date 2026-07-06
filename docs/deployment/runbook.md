# Deployment and Migration Runbook

This backend ships as a Spring Boot JAR inside a Docker image. CI verifies code
quality and builds artifacts; CD currently publishes container images and leaves
the actual remote deployment commands as placeholders to be wired for the target
infrastructure.

## Codepaths and Config

- `.github/workflows/ci.yml` - formatting, compile, tests, coverage, `bootJar`.
- `.github/workflows/cd.yml` - build and push Docker image to GHCR, then
  placeholder staging/production deploy steps.
- `.github/workflows/release.yml` - validate release tag, run release build,
  create GitHub Release with JAR artifact.
- `Dockerfile` - multi-stage JDK build and JRE runtime image.
- `docker-compose.yml` - local Postgres + API + Maildev.
- `docker-compose.prod.yml` - API-only production-style compose.
- `docker-compose.prod.full.yml` - VPS-style Postgres + API compose.
- `src/main/resources/application-prod.yml` - production runtime overrides.

## Branch and Image Flow

CD triggers:

| Trigger | Target | Notes |
| --- | --- | --- |
| Push to `develop` | Staging | Builds and pushes image, then runs staging placeholder. |
| Push to `main` | Production | Builds and pushes image, then uses the GitHub `production` environment. |
| Published release | Production | Builds image from release context and runs production placeholder. |

Images are pushed to:

```text
ghcr.io/<owner>/<repository>:<tag>
```

Additional tags are produced by `docker/metadata-action` and by the workflow's
version step:

- `dev-<short-sha>` for non-main branch pushes
- `main-<short-sha>` for `main`
- release tag name for release events

The deploy jobs in `cd.yml` currently echo placeholder success messages. Before
relying on CD for real deployments, replace the commented SSH/Kubernetes blocks
with the chosen deployment mechanism and keep the health checks enabled.

## Production Environment Variables

Start from `.env.example`, then replace all secrets and production-specific
values.

Required or high-impact values:

```bash
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require
DB_USERNAME=<user>
DB_PASSWORD=<password>
JWT_SECRET=<min-32-char-secret>
CORS_ALLOWED_ORIGINS=https://<frontend-host>
APP_URL=https://<frontend-host>
SWAGGER_ENABLED=false
ACTUATOR_ENDPOINTS=health,info,metrics,prometheus,caches
ACTUATOR_HEALTH_DETAILS=when_authorized
SERVER_PORT=8080
```

Mail is optional. If `MAIL_HOST` is empty in prod, the mail service logs
messages instead of sending through SMTP.

Management endpoints run on port `8081` in `application-prod.yml`. Override with
Spring Boot's `MANAGEMENT_SERVER_PORT` only if the compose port mapping and
health checks are changed at the same time.

## Database Migrations

Flyway behavior differs by environment:

- Base config enables Flyway, validates migrations, and allows out-of-order
  migrations for the existing `V8.1` migration.
- `FlywayConfig` calls `repair()` before `migrate()` when Flyway is enabled.
- `application-prod.yml` sets `spring.flyway.enabled=false` and
  `clean-disabled=true`; the production app does not auto-migrate on startup.

Run migrations before deploying a production image:

```bash
./gradlew flywayMigrate \
  -PDB_HOST=<host> \
  -PDB_PORT=5432 \
  -PDB_NAME=<database> \
  -PDB_USERNAME=<user> \
  -PDB_PASSWORD=<password>
```

For local database resets only:

```bash
./gradlew flywayReload
```

Do not run `flywayClean` or `flywayReload` against shared or production
databases.

## Docker Deployment Options

### API-only compose

Use `docker-compose.prod.yml` when PostgreSQL is managed separately:

```bash
cp .env.example .env
# edit .env with production values
docker compose -f docker-compose.prod.yml up -d --build
```

### Full VPS compose

Use `docker-compose.prod.full.yml` when Postgres runs beside the API on the same
host:

```bash
cp .env.example .env
# edit DB_NAME, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CORS, APP_URL, etc.
docker compose -f docker-compose.prod.full.yml up -d --build
```

The full compose file binds API and management ports to localhost:

```text
127.0.0.1:8080 -> API
127.0.0.1:8081 -> actuator/management
```

Put a reverse proxy in front of the API if it must be reachable publicly.

## Port and Health-Check Constraints

The Dockerfile exposes `8080` and `8081`, and compose health checks call:

```text
http://localhost:8080/actuator/health/liveness
```

`application-prod.yml` sets `server.port: 8090`, while `.env.example` sets
`SERVER_PORT=8080`. Keep `SERVER_PORT=8080` in production compose deployments
unless you also update:

- compose `ports`
- compose health checks
- reverse-proxy upstreams
- CD health checks

Management endpoints run on `8081` in prod through `management.server.port`.

## Persistent Files

Business logos are written under:

```text
BUSINESS_LOGOS_DIR=/app/data/uploads/business-logos
```

The Dockerfile creates this directory inside the container. For production,
mount a volume or host path there so uploads survive container replacement.

Example compose addition:

```yaml
services:
  api:
    volumes:
      - business_logos:/app/data/uploads/business-logos

volumes:
  business_logos:
```

## Post-Deploy Checks

Run these checks after each deployment:

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

For externally reachable deployments:

```bash
curl -fsS https://api.example.com/actuator/health
```

Then verify an authenticated tenant request and a subscription route. Swagger is
disabled in prod, so do not use it as a production readiness signal.

## Rollback Notes

Rollback is image-based:

1. Identify the previous GHCR image tag or digest.
2. Update the deployment to that image.
3. Restart the API container/deployment.
4. Re-run health checks.

Database migrations are forward-only in normal operation. If a rollback crosses
a schema change, confirm the old image can tolerate the migrated schema before
switching traffic back.
