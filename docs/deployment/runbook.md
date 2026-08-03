# Deployment runbook

Ops notes verified against `Dockerfile`, `docker-compose*.yml`, `application.yml`, `application-prod.yml`, `.env.example`, and `.github/workflows/cd.yml`.

## Compose files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Local: Postgres 16 + API (dev profile) + Maildev |
| `docker-compose.prod.yml` | Prod API only (external DB); loads `.env` |
| `docker-compose.prod.full.yml` | Prod Postgres + API on one VPS; DB not published; API bound to `127.0.0.1` |

None of the compose files mount persistent volumes for uploads. The Dockerfile creates `/app/data/uploads/business-logos` and `/app/data/uploads/product-images` inside the image/container filesystem — **recreating the container loses logos/images** unless you add a volume or bind mount.

## Dockerfile facts

- Multi-stage Temurin 17 Alpine build → `bootJar` (tests skipped in image build)
- Runtime user `appuser` (uid 1001)
- Default env:
  - `BUSINESS_LOGOS_DIR=/app/data/uploads/business-logos`
  - `PRODUCT_IMAGES_DIR=/app/data/uploads/product-images`
- Exposes `8080` and `8081`
- `HEALTHCHECK` probes `http://localhost:8080/actuator/health/liveness`

## Port / health-check pitfalls (prod)

From `application-prod.yml`:

- `server.port: 8090` (hardcoded in YAML)
- `management.server.port: 8081` (actuator moved off the main port)

Spring Boot env override: if `SERVER_PORT` is set (e.g. `.env.example` has `8080`), it typically overrides the YAML `8090`. Compose healthchecks and the Dockerfile `HEALTHCHECK` both hit **port 8080** on the **main** server path `/actuator/health/liveness`.

When `management.server.port=8081` is active, management endpoints live on **8081**, so a probe against `8080/actuator/...` can fail even if the app is healthy. Prefer probing `http://localhost:8081/actuator/health/liveness` under the prod profile, or align `SERVER_PORT` / management port / healthcheck explicitly.

## Flyway in production

`application-prod.yml`:

```yaml
spring.flyway.enabled: false
spring.flyway.clean-disabled: true
```

**Prod does not auto-migrate.** Run Flyway separately (CI job, ops script, or a one-off container with Flyway enabled) **before** rolling a release that needs new migrations. Dev/default profiles still migrate from `classpath:db/migration`.

## Env vars for uploads (often missing from `.env.example`)

Bound in `application.yml` via `AppFilesProperties`:

| Env var | Property | Default |
|---------|----------|---------|
| `BUSINESS_LOGOS_DIR` | `app.files.business-logos-dir` | `./data/uploads/business-logos` |
| `PRODUCT_IMAGES_DIR` | `app.files.product-images-dir` | `./data/uploads/product-images` |

These were historically absent from `.env.example` even though the Dockerfile sets them. Persist them with a volume, e.g.:

```yaml
volumes:
  - ecom360_uploads:/app/data/uploads
environment:
  BUSINESS_LOGOS_DIR: /app/data/uploads/business-logos
  PRODUCT_IMAGES_DIR: /app/data/uploads/product-images
```

## Required prod secrets / config

From prod YAML and `.env.example` (non-exhaustive):

- `JWT_SECRET` — required in prod (no default)
- `CORS_ALLOWED_ORIGINS` — required in prod
- `SPRING_DATASOURCE_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `SPRING_PROFILES_ACTIVE=prod`
- Optional mail: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`
- `APP_URL` — frontend base for email links

Swagger is disabled in prod (`springdoc.*.enabled: false`). Actuator exposure is limited (`health,info,metrics,prometheus,caches`) on the management port.

## CI/CD

`.github/workflows/cd.yml`:

- Push `develop` → staging image tags
- Push `main` / published release → production path (see workflow for approval gates)
- Builds/pushes to `ghcr.io/${{ github.repository }}` with branch / semver / sha tags

Image build uses the same `Dockerfile` (tests excluded via `-x test` in the image stage; CI quality gate is separate).

## Suggested deploy checklist

1. Apply pending Flyway migrations to the target DB (prod auto-migrate is off)
2. Confirm `JWT_SECRET`, CORS, DB URL
3. Confirm `SERVER_PORT` / management port match compose + healthchecks
4. Mount persistent storage for `PRODUCT_IMAGES_DIR` and `BUSINESS_LOGOS_DIR`
5. Roll the new image; verify `8081` (or chosen management port) liveness + a smoke auth call
6. Confirm public image URLs and commerce webhook URL still resolve behind the reverse proxy

## Codepaths

- `Dockerfile`
- `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.prod.full.yml`
- `src/main/resources/application.yml`, `application-prod.yml`
- `shared/infrastructure/config/AppFilesProperties.java`
- `.github/workflows/cd.yml`
- `.env.example`
