# Sales / POS workflow

Source-backed guide for `sales/**`. Verified against `SaleController`, `SaleService`, `SaleRequest`, `Sale`, `StockService`, and commerce import paths.

## Endpoints

Base: `/api/v1/sales` (JWT required).

| Method | Path | Permission | Behavior |
|--------|------|------------|----------|
| `POST` | `/` | `SALES_CREATE` | Create completed POS sale |
| `GET` | `/{id}` | `SALES_READ` | Get one sale (404 if outside plan history retention) |
| `GET` | `/` | `SALES_READ` | Paginated list (`storeId`, `periodStart`, `periodEnd`, `status`; max size 100; sort `createdAt` desc) |
| `PUT` | `/{id}` | `SALES_UPDATE` | Edit completed sale (receipt number kept) |
| `POST` | `/{id}/void` | `SALES_DELETE` | Void completed sale; restock |

Period filters on list convert `LocalDate` with `ZoneId.systemDefault()` (`periodEnd` is exclusive at start of next day).

## Create sale constraints

Request body (`SaleRequest`):

- `storeId` — required; must belong to the JWT business
- `clientId` — required for POS (`@NotNull` + service check)
- `paymentMethod` — required non-blank string
- `discountAmount` — optional, default `0`, min `0`
- `amountReceived` — optional, min `0`
- `note` — optional
- `lines` — non-empty; each line is `{ productId, quantity }` with `quantity >= 1`

Key rules in `SaleService.createSale`:

1. **Credit blocked on create** — `paymentMethod == "credit"` throws: *« Les ventes à crédit client ne sont pas disponibles au point de vente. »*
2. **Catalog unit price only** — line unit price is always `Product.salePrice`; request has no price field. Missing/`<= 0` sale price rejects the sale.
3. **Plan monthly quota** — if plan `maxSalesPerMonth` is not unlimited, count of sales created in the current calendar month (server TZ) must stay below the limit.
4. **Mobile money plan gate** — `wave` / `orange_money` require `plan.featureMultiPayment == true`.
5. **Stock deduction** — each line calls `StockService.updateStockForSale`; fails if `available - qty < 0` with a French insufficient-stock message.
6. **Discount** — after line totals, `discountAmount > subtotal` is rejected.
7. **Change** — if `amountReceived` is set: `changeGiven = max(0, amountReceived - total)`.
8. **Status** — new sales are always `"completed"`.
9. **Receipt** — `RCP-yyyyMMdd-NNNN` (random 4 digits, uniqueness checked).

## Update vs create pitfalls

`updateSale` is **not** symmetric with create:

| Rule | Create | Update |
|------|--------|--------|
| Credit payment | Hard-rejected | Rejected only when a plan exists and `featureClientCredits` is not true |
| Store change | N/A | Forbidden |
| Receipt number | Generated | Unchanged |
| Monthly sale quota | Enforced | Not re-checked (only mobile-money plan flag) |
| Stock | Deduct | Restock old lines (`updateStockForPurchase` / ref `EDIT-REV-{receipt}`), then deduct new lines |
| Client credit balance | N/A on create (credit blocked) | Deduct old credit total if was credit; add new total if becoming credit |

Only `status == "completed"` sales can be updated or voided. History retention clamp can hide or block edits (`isBeforeDataRetention`).

## Void

- Permission: `SALES_DELETE`
- Sets status `"voided"`
- Restocks via `updateStockForPurchase` with ref `VOID-{receipt}`
- If credit sale: deducts `sale.total` from client credit balance

## Payment methods (observed)

No enum validation on the string itself. Plan / business rules touch:

- `credit` — blocked on create; gated by `featureClientCredits` on update
- `wave`, `orange_money` — gated by `featureMultiPayment`
- Commerce import forces payment method `"web"` (see below)
- Dashboard aggregates completed revenue by whatever `paymentMethod` string is stored

## Stock interaction

`StockService.updateStockForSale`:

- Auto-creates a zero stock row if missing, then still rejects negative resulting quantity
- Records movement type `"sale"` with reference = sale UUID string

Void/edit restock uses movement type `"in"` via `updateStockForPurchase`.

## Commerce import path (not POS)

`SaleService.createSaleFromImport` (used by commerce webhooks):

- Skips POS permission checks
- Does **not** require a client (`clientId = null`)
- Uses imported `unitPriceMinorUnits` (site price), not catalog `salePrice`
- Requires each product’s `storeId` to match the connection store
- Still runs plan sale-quota + mobile-money validation via `validateSubscriptionForSale`
- Payment method fixed to `"web"` by `CommerceOrderIngestionService`

## Common failure modes (from recent POS fixes)

From `attach_client_to_order` / sale validation work and current service code:

1. Missing `clientId` → 400 validation / business rule
2. Product without valid `salePrice` → business rule naming the product
3. Discount larger than subtotal → business rule (checked after stock deduction attempt in create path — lines already processed in the same transaction, so the TX rolls back)
4. Insufficient stock → business rule with available vs requested quantities
5. Credit at POS create → always rejected even if plan supports client credits
6. Editing a voided sale → rejected
7. Changing `storeId` on update → rejected

## Codepaths

- `sales/infrastructure/web/SaleController.java`
- `sales/application/service/SaleService.java`
- `sales/application/dto/SaleRequest.java`, `SaleLineRequest.java`
- `sales/domain/model/Sale.java`
- `inventory/application/service/StockService.java` (`updateStockForSale` / `updateStockForPurchase`)
- `integration/commerce/application/service/CommerceOrderIngestionService.java`
