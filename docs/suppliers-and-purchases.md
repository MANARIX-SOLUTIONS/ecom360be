# Suppliers and purchase orders

Source-backed guide for `supplier/**` and the stock side effects on receive.
Verified against `SupplierController`, `SupplierService`, `PurchaseOrderController`,
`PurchaseOrderService`, `PurchaseOrder`, `Supplier`, `StockService`, and
`SubscriptionService.requireSupplierTracking`.

## Intent

- Keep a tenant supplier directory with a payable **balance**.
- Track purchase orders (PO) through a small status machine.
- On receive: restock the target store and increase the supplier balance by the PO total.
- Gate advanced PO tracking behind the plan flag `featureSupplierTracking`.

## Endpoints

### Suppliers — `/api/v1/suppliers` (JWT)

| Method | Path | Permission | Notes |
|--------|------|------------|-------|
| `POST` | `/` | `SUPPLIERS_CREATE` | Enforces `maxSuppliers` when the business has a plan |
| `GET` | `/` | `SUPPLIERS_READ` | Active suppliers only; optional `search`; max page size 100 |
| `GET` | `/{id}` | `SUPPLIERS_READ` | |
| `PUT` | `/{id}` | `SUPPLIERS_UPDATE` | |
| `DELETE` | `/{id}` | `SUPPLIERS_DELETE` | Hard delete |
| `POST` | `/{id}/payments` | `SUPPLIERS_UPDATE` | Record payment against balance |
| `GET` | `/{id}/payments` | `SUPPLIERS_READ` | Max page size 100 |

Supplier CRUD does **not** call `requireSupplierTracking`. A starter tenant can create
suppliers up to `maxSuppliers` (seeded as `10` for `starter`; `0` = unlimited on Pro/Business).

### Purchase orders — `/api/v1/purchase-orders` (JWT)

| Method | Path | Permission | Notes |
|--------|------|------------|-------|
| `POST` | `/` | `PURCHASE_ORDERS_CREATE` | Always starts as `draft` |
| `GET` | `/{id}` | `PURCHASE_ORDERS_READ` | |
| `GET` | `/` | `PURCHASE_ORDERS_READ` | Optional `status` **or** `supplierId` (status wins if both set); max size 100 |
| `PATCH` | `/{id}/status` | `PURCHASE_ORDERS_UPDATE` | Body: `{ "status": "<next>" }` |

All PO operations require `featureSupplierTracking` when a plan is present.
Without the flag the service throws `AccessDeniedException` with:
*« Commandes et suivi fournisseurs avancé non inclus dans votre plan. Passez au plan Pro. »*

`PURCHASE_ORDERS_DELETE` exists in `Permission` but there is **no** delete endpoint.

## Create PO request

```json
{
  "supplierId": "<uuid>",
  "storeId": "<uuid>",
  "expectedDate": "2026-08-15",
  "note": "optional",
  "lines": [
    { "productId": "<uuid>", "quantity": 10, "unitCost": 500 }
  ]
}
```

Constraints:

- `supplierId`, `storeId`, and non-empty `lines` are required.
- Each line: `quantity >= 1`, `unitCost >= 0` (minor units / CFA integer).
- Supplier and store must belong to the JWT business; product must belong to the business.
- Server sets `status = "draft"`, `totalAmount = sum(quantity * unitCost)`, and
  `reference = PO-#####` (zero-padded, uniqueness checked globally on `reference`).

## Status machine

```text
draft ──► ordered ──► received
  │            │
  └─► cancelled◄┘
```

| From | Allowed next | Side effects |
|------|--------------|--------------|
| `draft` | `ordered`, `cancelled` | None |
| `ordered` | `received`, `cancelled` | On `received`: stock in + supplier balance += `totalAmount`; `receivedDate = LocalDate.now()` |
| `received` / `cancelled` | *(none)* | Further transitions rejected |

Status body example:

```bash
curl -X PATCH "http://localhost:8080/api/v1/purchase-orders/<id>/status" \
  --header 'Authorization: Bearer <token>' \
  --header 'Content-Type: application/json' \
  --data '{"status":"received"}'
```

Invalid transitions throw `BusinessRuleException` (e.g. *Draft->ordered|cancelled only*).

## Receive side effects

When transitioning to `received`, for each PO line:

1. `StockService.updateStockForPurchase(productId, storeId, userId, qty, po.reference)`
   - Creates a zero stock row if missing.
   - Movement type `"in"`, quantity `+qty`, reference = PO reference
     (note string is always `"Purchase order received"`).
2. `supplier.balance += po.totalAmount` via `Supplier.addToBalance`.

There is **no** reverse path: cancelling after receive is forbidden, and there is no
unreceive API. Correcting a bad receive requires inventory adjustments and/or a
manual supplier payment/balance fix.

## Supplier payments (payables)

```http
POST /api/v1/suppliers/{id}/payments
```

```json
{ "amount": 15000, "paymentMethod": "cash", "note": "optional" }
```

- `amount >= 1`, `paymentMethod` required non-blank.
- Calls `Supplier.deductFromBalance(amount)` with **no non-negative floor** — overpayment
  can drive `balance` negative.
- Does not require `featureSupplierTracking` (only `SUPPLIERS_UPDATE`).

`balance` meaning in practice: increases when a PO is received (amount owed to the
supplier), decreases when a payment is recorded.

## Plan matrix (seeded V32)

| Plan | `maxSuppliers` | `featureSupplierTracking` |
|------|----------------|---------------------------|
| `starter` | 10 | `false` |
| `pro` | unlimited (`0`) | `true` |
| `business` | unlimited (`0`) | `true` |

If the business has **no** plan row, `requireSupplierTracking` and the
`maxSuppliers` check are both skipped (`ifPresent` only).

## Pitfalls

- List suppliers returns **active** rows only; inactive suppliers are hidden from
  the default list but remain fetchable by id.
- PO list filters are mutually preferential: when `status` is set, `supplierId` is ignored.
- Unit costs on PO lines are independent of catalog `purchasePrice` / `salePrice`.
- `updateStockForPurchase` is also reused by sale void/edit restock paths; movement
  `reference` distinguishes them (`PO-#####` vs `VOID-…` / `EDIT-REV-…`).
- Compose/Docker persistence for unrelated upload dirs does not affect supplier data
  (DB-backed), but stock movements are permanent once receive succeeds.

## Implementation map

- HTTP: `supplier/infrastructure/web/SupplierController`, `PurchaseOrderController`
- Services: `SupplierService`, `PurchaseOrderService`
- Domain: `Supplier`, `PurchaseOrder.transitionTo`, `PurchaseOrderLine`
- Stock: `inventory/application/service/StockService.updateStockForPurchase`
- Plan gate: `SubscriptionService.requireSupplierTracking`
