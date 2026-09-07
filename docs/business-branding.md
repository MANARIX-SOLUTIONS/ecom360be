# Business profile and custom branding

Tenant profile (`name`, `email`, `phone`, `address`, `logoUrl`) lives on
`business`. Custom branding is a **plan flag**, not an RBAC permission: only
`PROPRIETAIRE` (case-insensitive) or `ROLE_PLATFORM_ADMIN` can mutate profile
or logo.

## Intent

- Expose a single current-tenant profile for settings screens and printed
  receipts.
- Let Business-plan tenants set a logo either as an external URL or as a file
  hosted by the API.
- Serve hosted logos from `/api/v1/public/**` so `<img>` tags and print views
  do not need a bearer token.

## Profile contract

```http
GET  /api/v1/business/me
PUT  /api/v1/business/me
Authorization: Bearer <access-token>
```

| Constraint | Value |
| --- | --- |
| GET access | Any authenticated member of the current business (subscription must be granting) |
| PUT access | `proprietaire` / `PROPRIETAIRE` or platform admin |
| Required PUT fields | `name`, `email` (`@Email`, max 255) |
| Optional PUT fields | `phone` (max 50), `address` (max 1000) |
| Email uniqueness | Changing email fails if another `business` row already has that address |

`GET`/`PUT` do **not** expose `tax_id`, `currency`, or `locale` even though
those columns exist on `business`. `createdAt` is read-only.

Example:

```bash
curl -X PUT "http://localhost:8080/api/v1/business/me" \
  --header 'Authorization: Bearer <access-token>' \
  --header 'Content-Type: application/json' \
  --data '{"name":"Boutique Demo","email":"demo@example.com","phone":"+22500","address":"Abidjan"}'
```

## Logo: set URL or clear

```http
PATCH /api/v1/business/me/logo
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{ "logoUrl": "https://cdn.example.com/logo.png" }
```

| Body | Effect |
| --- | --- |
| `logoUrl` blank / omitted / whitespace | Clears `business.logo_url`. Deletes the previous **managed** file if the old URL is under this API's public prefix. **No** branding-plan check. |
| `logoUrl` non-empty (max 2048 chars) | Requires `plan.feature_custom_branding = true` when a granting subscription exists. Deletes any previous managed file, then stores the string as-is. |

There is no MIME or URL-format validation on PATCH. External CDN URLs are
allowed.

## Logo: upload file

```http
POST /api/v1/business/me/logo/upload
Authorization: Bearer <access-token>
Content-Type: multipart/form-data
```

| Constraint | Value |
| --- | --- |
| Role | Same as profile update (`PROPRIETAIRE` or platform admin) |
| Plan | `feature_custom_branding` must be true when a granting plan exists |
| Form field | `file` (`MultipartFile`) |
| Max size | 8 MB (`BusinessLogoStorageService.MAX_BYTES`, aligned with `spring.servlet.multipart.max-file-size`) |
| Allowed MIME | `image/png`, `image/jpeg`, `image/webp`, `image/gif` |
| Stored path shape | `/api/v1/public/business-logos/{businessId}/{uuid}.{ext}` |

```bash
curl -X POST "http://localhost:8080/api/v1/business/me/logo/upload" \
  --header 'Authorization: Bearer <access-token>' \
  --form 'file=@./logo.png;type=image/png'
```

On success the profile is returned with the new relative `logoUrl`. The
previous managed file is deleted first.

## Public download

```http
GET /api/v1/public/business-logos/{businessId}/{filename}
```

- No JWT. `SecurityConfig` permits `/api/v1/public/**`;
  `SubscriptionRequiredFilter` also skips `/api/v1/public/`.
- Filename must match `{uuid}.{png|jpg|jpeg|webp|gif}` or the handler returns
  `404`.
- Path traversal is rejected (`normalize` + prefix check).
- Successful responses set `Cache-Control: public, max-age=86400`.

## Plan gate

Seeded plans (`V32__update_plans_plans_abonnements.sql`):

| Slug | `feature_custom_branding` |
| --- | --- |
| `starter` | false |
| `pro` | false |
| `business` | true |

`SubscriptionService.getPlanForBusiness` returns empty when there is no
access-granting subscription. Both logo mutators use `ifPresent`, so **no
plan means the branding check is skipped**. Clearing the logo never hits the
gate.

There is no `Permission` enum for branding. Navigation/RBAC docs that list
`BUSINESS_USERS_*` do not apply here.

## Storage and operations

Configured via `app.files.business-logos-dir` / `BUSINESS_LOGOS_DIR`
(default `./data/uploads/business-logos`).

```text
{BUSINESS_LOGOS_DIR}/
  {businessId}/
    {uuid}.png
```

The Docker image sets `BUSINESS_LOGOS_DIR=/app/data/uploads/business-logos`
and creates the directory at build time. The storage bean fails startup if
that path cannot be created.

`docker-compose.yml`, `docker-compose.prod.yml`, and
`docker-compose.prod.full.yml` do **not** mount a volume on the upload
directory. Without a bind mount or named volume, uploaded logos disappear
when the container is recreated.

Only URLs that start with
`/api/v1/public/business-logos/{businessId}/` are treated as managed.
External URLs are left on disk (there is nothing to delete).

## Schema mismatch to watch

Flyway `V1` created `business.logo_url VARCHAR(500)`. The PATCH DTO allows
2048 characters. A typical managed path is well under 500 bytes; a long
external URL can fail at the database even after Bean Validation succeeds.

## Pitfalls

- JWT role codes are uppercase (`PROPRIETAIRE`). The service compares with
  `equalsIgnoreCase("proprietaire")`, so both forms work.
- `GET /api/v1/business/me` is subscription-gated. After trial expiry the
  client gets `402 SUBSCRIPTION_REQUIRED` and cannot read the profile, but
  the public logo URL still works.
- Upload MIME must be one of the four types; `application/octet-stream` is
  rejected.
- Public logos are intentionally unauthenticated. Do not store private
  documents on this path.
- Relative `logoUrl` values assume the frontend prefixes the API origin.
- Logo changes are not written to `audit_log`.

## Implementation map

- HTTP: `tenant/infrastructure/web/BusinessController`
- Public serve: `tenant/infrastructure/web/PublicBusinessLogoController`
- Disk I/O: `tenant/infrastructure/storage/BusinessLogoStorageService`
- Rules: `tenant/application/service/BusinessProfileService`
- Plan flag: `tenant/domain/model/Plan.featureCustomBranding`
- Config: `shared/infrastructure/config/AppFilesProperties`, `application.yml`
- Security allow-list: `identity/infrastructure/security/SecurityConfig`
