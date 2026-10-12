# API keys and outbound webhooks

Tenant registry for programmatic credentials and outbound webhook endpoints. Both resources are stored and listed. Nothing in this codebase authenticates a request with an API key, and nothing delivers an HTTP callback when a domain event happens.

Inbound commerce order webhooks are a different API (`/api/v1/commerce/**` and `/api/v1/public/commerce/webhooks/**`). Those verify HMAC and create sales. This page does not describe them.

## Codepaths

| Piece | Path |
| --- | --- |
| API keys | `ApiKeyController`, `ApiKeyService`, `ApiKey` |
| Outbound registry | `WebhookController`, `WebhookService`, `Webhook` |
| Plan gate | `SubscriptionService.requireFeatureApi` |
| Permissions | `Permission.API_KEYS_*`, `Permission.WEBHOOKS_*`, `RolePermissionService.require` |
| Schema | `V1__initial_schema.sql` (`api_key`, `webhook`) |
| Demo rows | `V27__seed_webhook.sql`, `V28__seed_api_key.sql` |

There are no tests for these controllers or services.

## Authentication and gates

Every route requires a bearer **JWT**. `JwtAuthenticationFilter` reads only `Authorization: Bearer`. `ApiKeyRepository.findByKeyHash` has no callers, so a raw `ecom360_…` key is not a credential.

`SubscriptionRequiredFilter` runs first. With no access-granting subscription (`trialing` or `active`, period end not before today) the response is HTTP **402** and code `SUBSCRIPTION_REQUIRED`. These paths are not exempt.

When a plan is present, `requireFeatureApi` rejects the call unless `plan.featureApi` is true:

- Seeded plans (`V32__update_plans_plans_abonnements.sql`): `starter` and `pro` are `false`, `business` is `true`.
- Failure is HTTP **403** (`AccessDeniedException`): `API, clés d'accès et webhooks sont réservés au plan Business.`
- The check uses `ifPresent`. If the business has no resolvable plan, the feature gate is skipped.
- The same check runs on list and get, not only on create.

Controllers have no `@PreAuthorize`. The service then calls `RolePermissionService.require`. Missing permission is HTTP **403** with `Accès refusé : permission <CODE> requise pour votre rôle`. Platform admins skip that permission check (`can` returns true). Rows are still stored under the JWT `businessId`. Login always attaches an accepted membership, so that id is set. A later refresh with no accepted membership can carry a null `businessId`. A platform admin in that state passes `requireBiz` (`hasBusinessAccess` is true for platform admins) and the insert then fails the non-null `business_id` column.

Default roles (`BusinessRoleBootstrapService` and `V54__business_roles_permissions.sql`):

| Role | API keys and webhooks |
| --- | --- |
| `PROPRIETAIRE` | All `API_KEYS_*` and `WEBHOOKS_*` |
| `GESTIONNAIRE` | Same. The only excluded codes are `SUBSCRIPTION_UPDATE` and `BUSINESS_USERS_DELETE`. |
| `CAISSIER` | None |

`NavigationPermissionRules` has no menu key for integrations. `settings:security` is gated by `STORES_READ`, not by `API_KEYS_*` or `WEBHOOKS_*`.

Rows are scoped by the JWT business id. `X-Business-Id` is unused. A missing business context (and the caller is not a platform admin) is HTTP **403** `Business context required`. Another tenant's id is HTTP **404** (`ApiKey not found with id: …` or `Webhook not found with id: …`).

No audit-log writer records these actions.

## API keys

Base path: `/api/v1/api-keys`.

| Method | Permission | Result |
| --- | --- | --- |
| `POST` | `API_KEYS_CREATE` | **201**. `rawKey` is returned once. |
| `GET` | `API_KEYS_READ` | All keys for the business, including revoked. `rawKey` is null. |
| `GET /{id}` | `API_KEYS_READ` | One key. `rawKey` is null. |
| `DELETE /{id}` | `API_KEYS_DELETE` | **204**. Sets `isActive` to false. The row stays. |

There is no update, rotate, or un-revoke endpoint.

Request body (`ApiKeyRequest`):

| Field | Constraint |
| --- | --- |
| `label` | Required, max 255. |
| `permissions` | Required, max 100. Stored as an opaque string. Not parsed, not checked against `Permission`. |
| `expiresAt` | Optional `LocalDate`. No `@Future`. A past date is accepted. |

Example:

```json
{
  "label": "Caisse back-office",
  "permissions": "read:products,read:sales",
  "expiresAt": "2026-12-31"
}
```

The `permissions` example matches the demo seed vocabulary. It is not the RBAC catalog (`PRODUCTS_READ`, and so on). Either string is stored the same way.

Create behavior:

- Raw key: `ecom360_` plus URL-safe Base64 (no padding) of 32 random bytes.
- Stored `key_hash`: SHA-256 of the raw key's UTF-8 bytes, then standard Base64 (with padding). Unique.
- `isActive` is set to true. There is no plan cap on how many keys a business can hold.
- `ApiKey.isExpired()` (`expiresAt` before `LocalDate.now()`, JVM default zone) is never called. A past `expiresAt` does not flip `isActive` and does not hide the row.

`V28` inserts one row for demo business `a0000001-0000-4000-8000-000000000001` (`Demo Commerce SARL`): label `Clé démo`, permissions `read:products,read:sales`, hash `demo_api_key_hash_placeholder`, active. That hash is not the digest of a known key. The demo subscription (`V6`) is the `starter` plan, so the management API returns **403** from `requireFeatureApi` while that subscription still grants access.

## Outbound webhooks

Base path: `/api/v1/webhooks`.

| Method | Permission | Result |
| --- | --- | --- |
| `POST` | `WEBHOOKS_CREATE` | **201**. `secret` is returned once. |
| `GET` | `WEBHOOKS_READ` | All rows, active and inactive. No secret. |
| `GET /{id}` | `WEBHOOKS_READ` | One row. No secret. |
| `PUT /{id}` | `WEBHOOKS_UPDATE` | Replaces `url`, `events`, and `isActive`. Does not rotate the secret. |
| `DELETE /{id}` | `WEBHOOKS_DELETE` | **204**. Hard delete. |

Request body (`WebhookRequest`):

| Field | Constraint |
| --- | --- |
| `url` | Required, max 500. No scheme or host check. Column is `VARCHAR(500)`. |
| `events` | Required, max 500. Opaque string. No event catalog. |
| `isActive` | Omitted or null becomes `true` (compact constructor). |

Example:

```json
{
  "url": "https://example.com/hooks/ecom360",
  "events": "sale.created,sale.updated",
  "isActive": true
}
```

`sale.created` and `sale.updated` appear only in `V27__seed_webhook.sql`. No service calls `AggregateRoot.registerEvent`, and no HTTP client posts to `webhook.url`. `WebhookRepository.findByBusinessIdAndIsActive` has no callers. A `PUT` that omits `isActive` reactivates the endpoint. Duplicate URLs are allowed (no unique constraint).

Create stores SHA-256 of the secret's UTF-8 bytes as standard Base64 in `secret_hash`. The raw secret is URL-safe Base64 (no padding) of 32 random bytes, with no prefix. Update leaves `secret_hash` unchanged, and later responses never include `secret`.

`V27` inserts an **inactive** row for the same demo business: `https://example.com/webhook/demo`, events `sale.created,sale.updated`, `secret_hash` literally `demo_secret_hash` (not a digest). The migration comment says idempotent; the SQL has no `ON CONFLICT`.

## Failure modes

| Symptom | Cause |
| --- | --- |
| **401** | Missing or invalid access JWT. An API key in `Authorization` is not accepted. |
| **402** `SUBSCRIPTION_REQUIRED` | No current access-granting subscription. Happens before the plan-feature check. |
| **403** plan message | `starter` or `pro` (`featureApi` false), including the seeded demo tenant. |
| **403** permission message | Cashier, or a custom role without the matching `API_KEYS_*` / `WEBHOOKS_*` code. |
| **400** | Bean validation (`label`, `permissions`, `url`, `events`). |
| **404** | Id not in the caller's business. |
| Partner never receives a callback | Expected. The registry is not dispatched. |
| Integrating with the raw key returns **401** | Expected. `findByKeyHash` is unused. |
| Revoked key still listed | `GET` uses `findByBusinessId` and does not filter `isActive`. |
| `expiresAt` in the past, `isActive` still true | `isExpired()` is unused. |
