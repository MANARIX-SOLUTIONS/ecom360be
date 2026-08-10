# Client credits (receivables)

Source-backed guide for how client credit balances are created, adjusted, and repaid.
Verified against `SaleService`, `Sale`, `ClientService`, `Client`, `ClientController`,
`ClientRepository.sumPositiveCreditBalance`, `DashboardService`, and
`Plan.featureClientCredits`.

Complements the POS sales guide: create always rejects `credit`; this page documents
the practical credit lifecycle and repayment APIs.

## Intent

- Track money a customer owes the business as `Client.creditBalance`.
- Open credit by converting a completed sale to `paymentMethod = "credit"` (update path).
- Collect repayments via `POST /clients/{id}/payments`.
- Surface receivables on the dashboard as the sum of **positive** active credit balances.

## Credit sale definition

`Sale.isCreditSale()` is true only when:

```text
paymentMethod == "credit" AND clientId != null
```

POS always requires `clientId`, so a credit POS sale is simply `paymentMethod: "credit"`.
Commerce import sets `clientId = null`, so imported sales never become credit sales.

## How credit enters the system

| Path | Behavior |
|------|----------|
| `POST /api/v1/sales` with `paymentMethod: "credit"` | **Always rejected** — *« Les ventes à crédit client ne sont pas disponibles au point de vente. »* |
| `PUT /api/v1/sales/{id}` setting `paymentMethod: "credit"` | Allowed when plan has `featureClientCredits` (see gate below); adds `sale.total` to the client balance |
| Shared `persistSaleFromLineSpecs` | Still contains `addCredit` logic, but POS create never reaches it with credit |

### Practical workflow today

1. Create a normal completed sale (`cash`, `wave`, …) with a client.
2. Update that sale to `paymentMethod: "credit"` (same store, required `clientId`, lines as needed).
3. Collect later with `POST /clients/{id}/payments`.

```bash
# 1) create non-credit sale, then:
curl -X PUT "http://localhost:8080/api/v1/sales/<sale-id>" \
  --header 'Authorization: Bearer <token>' \
  --header 'Content-Type: application/json' \
  --data '{
    "storeId": "<store-id>",
    "clientId": "<client-id>",
    "paymentMethod": "credit",
    "discountAmount": 0,
    "lines": [{ "productId": "<product-id>", "quantity": 1 }]
  }'
```

## Plan / permission gates

| Action | Permission | Plan gate |
|--------|------------|-----------|
| Update sale to `credit` | `SALES_UPDATE` | If a plan exists and `featureClientCredits != true` → rejected |
| Record client payment | `CLIENTS_UPDATE` | Same `featureClientCredits` check |
| List/get payments | `CLIENTS_READ` | No feature flag |
| Dashboard `totalReceivable` | dashboard permissions | Read-only aggregate; no extra flag |

Gate implementation uses `getPlanForBusiness(...).ifPresent(...)`:

- **No plan** → credit update and client payments are **not** blocked by the feature flag.
- **Starter** (seeded) → `featureClientCredits = false`.
- **Pro / Business** → `featureClientCredits = true`.

## Balance mutations

Amounts are integer minor units (same as sale totals).

| Event | Client balance change |
|-------|------------------------|
| Sale becomes credit (create path if ever reached, or update) | `+ sale.total` (`addCredit`) |
| Sale was credit and is updated | `- oldTotal` on the **previous** client, then `+ newTotal` if still credit |
| Credit sale voided | `- sale.total` (`deductCredit`) |
| `POST /clients/{id}/payments` | `- amount` (`deductCredit`) |

`addCredit` / `deductCredit` have **no floor or ceiling**. Over-repayment can drive
`creditBalance` negative; there is no validation that `amount <= creditBalance`.

Client change on sale update: if the old sale was credit, the old client's balance is
reduced by the old total before the new credit total is applied to the (possibly new) client.

## Repayment API

```http
POST /api/v1/clients/{id}/payments
```

```json
{
  "storeId": "<uuid>",
  "amount": 5000,
  "paymentMethod": "cash",
  "note": "optional"
}
```

Constraints:

- `storeId` required (`@NotNull`) — stored on the payment row; not re-validated against stock.
- `amount >= 1`
- `paymentMethod` required non-blank (free string; no enum)
- Response `201` with payment id, amounts, timestamps

List:

```http
GET /api/v1/clients/{id}/payments?page=0&size=20
```

Max page size 100. Payments are ordered by `createdAt` descending.

## Dashboard receivable

`DashboardService` sets:

```text
totalReceivable = ClientRepository.sumPositiveCreditBalance(businessId)
```

That query sums `creditBalance` only for **active** clients with `creditBalance > 0`.
Negative balances (overpayment) do not reduce the aggregate.

## Pitfalls

- Frontends that send `credit` on create will always fail; use update-after-create.
- Plan gate asymmetry: create hard-rejects credit even on Pro; only update is plan-gated.
- Voiding a credit sale reduces the client balance but does **not** delete payment history.
- Payments are not linked to a specific sale — they only adjust the running balance.
- `featureClientCredits` false blocks payment recording even if a legacy positive balance exists.
- Sale update restocks/re-deducts inventory like a void+recreate; changing lines on a
  credit conversion has stock side effects unrelated to the credit flag.

## Implementation map

- Sales: `sales/application/service/SaleService` (`createSale`, `updateSale`, `voidSale`, `persistSaleFromLineSpecs`)
- Sale flag: `sales/domain/model/Sale.isCreditSale`
- Clients: `client/application/service/ClientService.recordPayment`
- Model: `client/domain/model/Client.addCredit` / `deductCredit`
- Dashboard: `analytics/application/service/DashboardService` + `ClientRepository.sumPositiveCreditBalance`
- Plan field: `tenant/domain/model/Plan.featureClientCredits` (seeded in Flyway V32)
