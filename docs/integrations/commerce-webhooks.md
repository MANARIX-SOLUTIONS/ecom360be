# Commerce order webhooks

Public inbound order ingestion. Verified against `PublicCommerceOrderWebhookController`, `CommerceWebhookHmacVerifier`, `CommerceOrderIngestionService`, `CommerceConnectionService`, `SecurityConfig`, and `V56`/`V57`.

## Public endpoints (no JWT)

Base: `/api/v1/public/commerce/webhooks` (`SecurityConfig` permits `/api/v1/public/**`).

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/incoming/{incomingToken}` | Canonical JSON order |
| `POST` | `/incoming/{incomingToken}/woocommerce` | WooCommerce order JSON → mapped to canonical |

Responses:

- **202 Accepted** — processed (`PROCESSED`)
- **200 OK** — duplicate skipped (`DUPLICATE_SKIPPED`) with existing `saleId`

Managing connections (create token/secret, list, update, delete) is authenticated under `/api/v1/commerce/connections` and requires `COMMERCE_CONNECTIONS_*` (seeded for `PROPRIETAIRE` / `GESTIONNAIRE` only).

On connection create, the API returns an `incomingWebhookUrl` of the form:

`/api/v1/public/commerce/webhooks/incoming/{token}`

## HMAC authentication

Header: `X-Commerce-Signature` (`ApiConstants.X_COMMERCE_SIGNATURE`).

Algorithm (`CommerceWebhookHmacVerifier`):

1. HMAC-SHA256 over the **raw request body** bytes as UTF-8, using the connection `hmac_secret`
2. Encode digest as **lowercase hex**
3. Accept header forms: `<hex>`, `sha256=<hex>`, or `v1=<hex>` (prefix case-insensitive; hex lowercased)

Missing / invalid signature → `UnauthorizedCommerceWebhookException`.

The controller binds `@RequestBody String rawBody` so verification uses the exact bytes Spring already read as a String (callers must sign the same JSON bytes they send).

## Canonical payload constraints

`CanonicalOrderPayload` (summary of enforced fields):

- `sourceType`, `externalOrderId`, `currency`, `paymentStatus` required
- At least one line; each line needs `sku` **or** `productId`
- Eligible `paymentStatus` values (case-insensitive) for sale creation:
  - `paid`, `completed`, `captured`, `settled`, `paid_in_full`, `processing`
- Other statuses → business rule (no sale)

Source-type matching:

- Connection `GENERIC_WEBHOOK` accepts any payload `sourceType`
- Otherwise payload `sourceType` must match the connection

## Processing pipeline

1. Resolve connection by `incomingToken`
2. Verify HMAC
3. Parse / map payload (WooCommerce mapper for the `/woocommerce` route)
4. Reject unpaid / ineligible payment status
5. Idempotency: successful prior ingestion for `(connectionId, externalOrderId)` → `DUPLICATE_SKIPPED`
6. Resolve each line to an active product in the connection’s store (by `productId` or normalized SKU)
7. Create sale via `SaleService.createSaleFromImport` with payment method **`web`**, discount `0`, no client
8. Persist `CommerceOrderIngestionLog` with status `PROCESSED` and `saleId`

Imported lines use **payload unit prices** (`unitPriceMinorUnits`), not catalog `salePrice`. Products must belong to the connection store.

## Permissions note

Public webhook endpoints intentionally skip JWT and POS `SALES_CREATE`. Tenant isolation is via secret URL token + HMAC. Connection CRUD still requires commerce permissions on an authenticated user.

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| 401-style unauthorized commerce error | Missing/wrong `X-Commerce-Signature`, or body mutated after signing |
| Payment status business rule | Status not in the eligible set (e.g. `pending`) |
| SKU not found | No active product with that SKU in the connection store |
| Product store mismatch | `productId` belongs to another store |
| Duplicate 200 with old `saleId` | Idempotent replay of same `externalOrderId` |
| Plan limit on import | Monthly max-sales quota still applied in `createSaleFromImport` |

## Codepaths

- `integration/commerce/infrastructure/web/PublicCommerceOrderWebhookController.java`
- `integration/commerce/application/service/CommerceWebhookHmacVerifier.java`
- `integration/commerce/application/service/CommerceOrderIngestionService.java`
- `integration/commerce/woocommerce/WooCommerceOrderMapper.java`
- `shared/infrastructure/web/ApiConstants.java` (`X-Commerce-Signature`)
- `db/migration/V56__commerce_order_integration.sql`, `V57__commerce_hmac_and_permissions.sql`
