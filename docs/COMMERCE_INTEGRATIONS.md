# Commerce integrations

Commerce connections let external shops push paid orders into 360 PME Commerce.
The webhook pipeline verifies a shared-secret HMAC signature, normalizes the
payload, resolves products in the linked store, and creates a sale.

Primary codepaths:

- `integration/commerce/infrastructure/web/CommerceConnectionController.java`
- `integration/commerce/infrastructure/web/PublicCommerceOrderWebhookController.java`
- `integration/commerce/application/service/CommerceConnectionService.java`
- `integration/commerce/application/service/CommerceOrderIngestionService.java`
- `integration/commerce/application/service/CommerceWebhookHmacVerifier.java`
- `integration/commerce/application/dto/CanonicalOrderPayload.java`
- `integration/commerce/woocommerce/WooCommerceOrderMapper.java`
- `src/main/resources/db/migration/V56__commerce_order_integration.sql`
- `src/main/resources/db/migration/V57__commerce_hmac_and_permissions.sql`

## Creating a connection

Authenticated tenant users create connections with:

```http
POST /api/v1/commerce/connections
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "storeId": "5f51a98c-2cb8-47cf-8a51-9b2f9e4c35ad",
  "sourceType": "GENERIC_WEBHOOK",
  "label": "Custom storefront"
}
```

Required permission: `COMMERCE_CONNECTIONS_CREATE`.

The response includes:

- `incomingWebhookPath`: public path to configure in the external shop.
- `hmacSecret`: shared secret used to sign webhook payloads.

`hmacSecret` is returned only at creation time. Store it securely in the external
connector.

## Public webhook endpoints

Public webhook endpoints do not use JWT. They authenticate with:

1. an incoming URL token in the path;
2. `X-Commerce-Signature`, an HMAC-SHA256 signature over the exact raw request
   body.

| Method | Path | Payload |
| --- | --- | --- |
| `POST` | `/api/v1/public/commerce/webhooks/incoming/{incomingToken}` | Canonical order JSON |
| `POST` | `/api/v1/public/commerce/webhooks/incoming/{incomingToken}/woocommerce` | WooCommerce order JSON |

The WooCommerce path requires the connection `sourceType` to be `WOOCOMMERCE`.

## HMAC signature

The signature is:

- algorithm: HMAC-SHA256;
- key: the connection `hmacSecret`;
- message: the exact raw UTF-8 request body;
- encoding: lowercase hex.

Accepted header formats:

```text
X-Commerce-Signature: <hex>
X-Commerce-Signature: sha256=<hex>
X-Commerce-Signature: v1=<hex>
```

Example:

```bash
body='{"sourceType":"GENERIC_WEBHOOK","externalOrderId":"EXT-1001","currency":"XOF","paymentStatus":"paid","lines":[{"sku":"SKU-001","label":"T-shirt","quantity":2,"unitPriceMinorUnits":5000}]}'
secret='connection-secret'
signature=$(printf '%s' "$body" | openssl dgst -sha256 -hmac "$secret" -binary | xxd -p -c 256)

curl -X POST "http://localhost:8080/api/v1/public/commerce/webhooks/incoming/<incomingToken>" \
  -H "Content-Type: application/json" \
  -H "X-Commerce-Signature: $signature" \
  --data "$body"
```

Do not pretty-print, reserialize, or alter whitespace after computing the
signature. The backend verifies the raw bytes received by the controller.

## Canonical payload

The generic endpoint expects:

```json
{
  "sourceType": "GENERIC_WEBHOOK",
  "externalOrderId": "EXT-1001",
  "externalUpdatedAt": "2026-06-01T12:00:00Z",
  "currency": "XOF",
  "paymentStatus": "paid",
  "customer": {
    "name": "Awa Diallo",
    "email": "awa@example.com",
    "phone": "+221771234567",
    "address": "Dakar"
  },
  "shipping": {
    "amountMinorUnits": 1500,
    "method": "standard"
  },
  "lines": [
    {
      "sku": "SKU-001",
      "label": "T-shirt",
      "quantity": 2,
      "unitPriceMinorUnits": 5000
    }
  ],
  "metadata": {
    "source": "custom-shop"
  }
}
```

Required fields:

- `sourceType`: max 64 characters.
- `externalOrderId`: max 512 characters.
- `currency`: max 16 characters.
- `paymentStatus`: max 32 characters.
- `lines`: non-empty.

Each line must include:

- `label`;
- `quantity` >= 1;
- `unitPriceMinorUnits` >= 0;
- either `productId` or `sku`.

`productId` must belong to the connection's store. `sku` resolves against active
products in the connection's store.

## Payment status constraints

The ingestion service creates a sale only for payment statuses treated as paid:

- `paid`
- `completed`
- `captured`
- `settled`
- `paid_in_full`
- `processing`

Other statuses are logged as validation failures and rejected with a business
rule error.

## Idempotency and responses

Successful ingestion is idempotent by `connection_id` + `externalOrderId`.

| Result | HTTP status | Response status |
| --- | --- | --- |
| New sale created | `202 Accepted` | `PROCESSED` |
| Already processed | `200 OK` | `DUPLICATE_SKIPPED` |

The ingestion log stores the payload hash and up to 12,000 characters of raw
payload for audit/debugging.

## Permissions and plan gates

Connection management uses:

- `COMMERCE_CONNECTIONS_CREATE`
- `COMMERCE_CONNECTIONS_READ`
- `COMMERCE_CONNECTIONS_UPDATE`
- `COMMERCE_CONNECTIONS_DELETE`

API/webhook features are plan-gated through `SubscriptionService.requireFeatureApi`.
Current plan seed data enables API features for the Business plan.

## Troubleshooting

### 401/unauthorized webhook

Check `X-Commerce-Signature`:

- The secret must be the creation-time `hmacSecret`.
- The signature must be computed over the exact raw body.
- Hex, `sha256=<hex>`, and `v1=<hex>` are accepted.

### Product not found

Ensure each line has either:

- a `productId` belonging to the connection's store and active; or
- a `sku` matching an active product in the connection's store.

### WooCommerce endpoint rejects source type

The `/woocommerce` endpoint only accepts connections whose `sourceType` is
`WOOCOMMERCE`.
