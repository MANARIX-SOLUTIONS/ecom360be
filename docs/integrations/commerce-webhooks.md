# Commerce Webhook Ingestion

Commerce connections let external storefronts import paid orders into the POS
sales workflow. The public webhook endpoints do not use JWT; they are secured
with an opaque URL token and an HMAC signature over the raw request body.

## Codepaths

- `integration/commerce/infrastructure/web/CommerceConnectionController.java`
  creates and lists tenant commerce connections.
- `integration/commerce/infrastructure/web/PublicCommerceOrderWebhookController.java`
  accepts public webhook calls.
- `integration/commerce/application/service/CommerceWebhookHmacVerifier.java`
  verifies `X-Commerce-Signature`.
- `integration/commerce/application/service/CommerceOrderIngestionService.java`
  validates payloads, enforces idempotency, resolves products, and creates sales.
- `integration/commerce/woocommerce/WooCommerceOrderMapper.java` maps
  WooCommerce orders to the canonical payload.

## Create a Connection

Create connections through the protected tenant API:

```http
POST /api/v1/commerce/connections
Authorization: Bearer <jwt>
Content-Type: application/json
```

The create response returns:

- a relative `incomingWebhookPath`, such as
  `/api/v1/public/commerce/webhooks/incoming/{token}`
- an HMAC secret displayed only once at creation time

Store the HMAC secret in the external commerce system immediately. Subsequent
connection reads return the path and metadata, not the raw secret.

## Public Endpoints

Canonical JSON:

```http
POST /api/v1/public/commerce/webhooks/incoming/{incomingToken}
Content-Type: application/json
X-Commerce-Signature: sha256=<hex-hmac>
```

WooCommerce order JSON:

```http
POST /api/v1/public/commerce/webhooks/incoming/{incomingToken}/woocommerce
Content-Type: application/json
X-Commerce-Signature: sha256=<hex-hmac>
```

The WooCommerce path requires the stored connection `sourceType` to be
`WOOCOMMERCE`. The canonical path accepts connections where `sourceType` matches
the payload `sourceType`, except `GENERIC_WEBHOOK`, which is accepted as a
generic source.

## Signature Contract

`X-Commerce-Signature` must be HMAC-SHA256 over the exact raw UTF-8 request body
using the connection HMAC secret.

Accepted header formats:

- `<lowercase-hex>`
- `sha256=<lowercase-hex>`
- `v1=<lowercase-hex>`

Example:

```bash
body='{"sourceType":"GENERIC_WEBHOOK","externalOrderId":"web-1001","currency":"XOF","paymentStatus":"paid","lines":[{"sku":"SKU-001","label":"Savon","quantity":2,"unitPriceMinorUnits":1500}]}'
signature="$(printf '%s' "$body" | openssl dgst -sha256 -hmac "$HMAC_SECRET" -binary | xxd -p -c 256)"

curl -i \
  -H "Content-Type: application/json" \
  -H "X-Commerce-Signature: sha256=$signature" \
  -d "$body" \
  "https://api.example.com/api/v1/public/commerce/webhooks/incoming/$INCOMING_TOKEN"
```

Important constraints:

- Sign the exact bytes sent on the wire. Reformatting JSON after signing will
  invalidate the signature.
- Missing, malformed, or mismatched signatures are rejected before ingestion.
- The incoming URL token identifies the connection; keep it private too.

## Canonical Payload

Minimum accepted shape:

```json
{
  "sourceType": "GENERIC_WEBHOOK",
  "externalOrderId": "web-1001",
  "currency": "XOF",
  "paymentStatus": "paid",
  "lines": [
    {
      "sku": "SKU-001",
      "label": "Savon",
      "quantity": 2,
      "unitPriceMinorUnits": 1500
    }
  ]
}
```

Fields enforced by validation:

| Field | Constraint |
| --- | --- |
| `sourceType` | Required, max 64 chars. Must match connection source type unless the connection is `GENERIC_WEBHOOK`. |
| `externalOrderId` | Required, max 512 chars. Used for idempotency per connection. |
| `currency` | Required, max 16 chars. |
| `paymentStatus` | Required, max 32 chars. Must be eligible for sale import. |
| `lines` | Required, non-empty. |
| `lines[].productId` or `lines[].sku` | One is required for every line. |
| `lines[].label` | Required, max 500 chars. |
| `lines[].quantity` | Required, minimum 1. |
| `lines[].unitPriceMinorUnits` | Required, minimum 0. |

Payment statuses eligible for sale import:

- `paid`
- `completed`
- `captured`
- `settled`
- `paid_in_full`
- `processing`

## Product Resolution and Sale Creation

For each line, ingestion resolves the product within the connection business and
store:

1. If `productId` is present, the product must belong to the same business and
   store and must be active.
2. Otherwise, `sku` is matched against an active product in the same business
   and store.

After validation, the service creates a sale through `SaleService` with payment
method `web` and a note referencing the external order ID and source type.

## Idempotency and Responses

Successful ingestions are keyed by connection and `externalOrderId`.

- First successful import returns HTTP 202 with status `PROCESSED` and a
  `saleId`.
- Replays of the same successful external order return HTTP 200 with status
  `DUPLICATE_SKIPPED` and the original `saleId`.

Response shape:

```json
{
  "status": "PROCESSED",
  "ingestionLogId": "00000000-0000-0000-0000-000000000000",
  "externalOrderId": "web-1001",
  "message": "Vente enregistrée.",
  "saleId": "00000000-0000-0000-0000-000000000000"
}
```

The raw payload is stored on the ingestion log, truncated to 12,000 characters.
Validation failures are logged by the failure logger when enough payload context
is available.

## Troubleshooting

| Symptom | Likely cause | Check |
| --- | --- | --- |
| Unauthorized signature error | Header missing, invalid hex, or body changed after signing. | Recompute HMAC from the exact request body. |
| Connection not found | Wrong incoming token or deleted connection. | List tenant commerce connections and compare `incomingWebhookPath`. |
| Connection disabled | `isActive` is false. | Re-enable or recreate the connection. |
| WooCommerce source error | Non-`WOOCOMMERCE` connection called the WooCommerce path. | Use the canonical path or create a WooCommerce connection. |
| Product not found | SKU/product ID does not match an active product in the connection store. | Confirm store assignment and product active status. |
| Duplicate skipped | Same external order already processed successfully. | Treat HTTP 200 `DUPLICATE_SKIPPED` as idempotent success. |
