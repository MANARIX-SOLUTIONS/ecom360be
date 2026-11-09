# Inventory stock

On-hand quantity is one `product_store_stock` row per `(product_id, store_id)`. Each change made through `StockService` also inserts a `stock_movement`. Nothing in the plan limits how many rows or how much quantity a business can hold. `feature_stock_alerts` only hides dashboard lists.

The tenant is the JWT `businessId`. `X-Business-Id` is not read. Store assignment (`business_user_store`) is not applied on stock routes, so a member who knows a store id can read or change that store even when `GET /api/v1/stores` would hide it.

## Routes

All paths are under `/api/v1/stock` and require a JWT. Init and adjust return HTTP 201. Reads return HTTP 200.

| Method | Path | Permission | Body |
| --- | --- | --- | --- |
| `POST` | `/init` | `STOCK_INIT` | `StockLevelResponse` |
| `POST` | `/adjust` | `STOCK_ADJUST` | `StockMovementResponse` |
| `GET` | `/store/{storeId}` | `STOCK_READ` | Page, or a JSON array when `productIds` is set |
| `GET` | `/product/{productId}/store/{storeId}` | `STOCK_READ` | One level. HTTP 404 `Stock not found` when the row is missing |
| `GET` | `/movements/product/{productId}/store/{storeId}` | `STOCK_READ` | Page, `createdAt` descending |
| `GET` | `/movements/store/{storeId}` | `STOCK_READ` | Page, `createdAt` descending |

There is no route to change `minStock` later, no delete, and no list across every store.

Paged reads default to `page=0` and `size=20`. `size` is capped at 100. The store level page has no `ORDER BY`.

`GET /store/{storeId}?productIds={id}&productIds={id}` skips paging and `search` and returns a JSON array of rows that already exist. Unknown ids are dropped. An empty `productIds` list falls through to the paged query.

`search` matches product name, sku, or barcode, case-insensitive contains. Inactive products stay in the result. `%` and `_` are still SQL `LIKE` wildcards.

## Initialize

```json
{
  "productId": "6d0f0a1e-1c2b-4d5e-8f70-111111111111",
  "storeId": "7e1a1b2f-2d3c-5e6f-9071-222222222222",
  "quantity": 12,
  "minStock": 2
}
```

`quantity` is required and must be `>= 0` (HTTP 400). Omitted `minStock` is stored as 0. A negative `minStock` is HTTP 400.

The product and the store must belong to the JWT business. Inactive products and inactive stores are accepted. `product.storeId` does not have to equal the stock `storeId`.

A second init of the same pair is HTTP 422 `Stock already initialized for this product/store`. Two requests that both pass the existence check can still hit `UNIQUE(product_id, store_id)` and then HTTP 500 from the generic handler.

`quantity > 0` writes one movement: type `in`, signed quantity equal to that quantity, before `0`, reference null, note `Initial stock`. `quantity == 0` writes no movement. `minStock` is stored only on this call.

## Adjust

```json
{
  "productId": "6d0f0a1e-1c2b-4d5e-8f70-111111111111",
  "storeId": "7e1a1b2f-2d3c-5e6f-9071-222222222222",
  "quantity": 4,
  "type": "adjustment",
  "reference": "INV-1",
  "note": "count"
}
```

`type` is required and case-sensitive. `reference` is optional. The column is `VARCHAR(100)`.

| `type` | What `quantity` means | Missing row |
| --- | --- | --- |
| `in` | Units to add. The sign is ignored (`Math.abs`). | HTTP 404 `Stock not initialized` |
| `out` | Units to remove. The sign is ignored. | HTTP 404 `Stock not initialized` |
| `adjustment` | New on-hand level. Must be `>= 0` or HTTP 422 `Quantity cannot be negative`. | Row is created at quantity 0 and `minStock` 0, then set to this level |
| anything else | | HTTP 422 `Invalid type` |

`before + delta < 0` is HTTP 422 `Insufficient stock`. Reaching 0 is allowed. A zero delta still inserts a movement.

On-hand 10 with `type=adjustment` and `quantity=4` stores movement quantity `-6` and on-hand 4. The same on-hand with `type=in` and `quantity=-3` adds 3.

## Sales and receipts

`SaleService` and `PurchaseOrderService` call `updateStockForSale` and `updateStockForPurchase` directly. Those helpers do not check `STOCK_*`. They participate in the caller's `@Transactional` method, so a later failure rolls back the row and the movement.

`updateStockForSale` creates a 0 / `minStock` 0 row when none exists, then rejects `onHand - qty < 0` with HTTP 422 `Stock insuffisant pour ce produit (disponible: {before}, demandé: {qty})`. POS lines and commerce lines both require quantity `>= 1`, so a sale against a missing row fails and does not leave the 0 row behind.

`updateStockForPurchase` creates the same empty row when needed, then adds. It does not refuse a missing row.

| Caller | Permission checked | Movement |
| --- | --- | --- |
| `POST /api/v1/sales` | `SALES_CREATE` | type `sale`, quantity `-qty`, reference the sale UUID, note null |
| `PUT /api/v1/sales/{id}` | `SALES_UPDATE` | one `in` per old line, then one `sale` per new line |
| `POST /api/v1/sales/{id}/void` | `SALES_DELETE` | one `in` per line |
| `PATCH /api/v1/purchase-orders/{id}/status` with `{"status":"received"}` | `PURCHASE_ORDERS_UPDATE`, and the supplier-tracking feature | one `in` per line |

The purchase helper always writes type `in` and note `Purchase order received`. The reference depends on the caller:

- receive: the PO reference (`PO-` plus the counter from `PurchaseOrderService.genRef`)
- void: `VOID-` plus the receipt (`RCP-yyyyMMdd-####`)
- sale edit: `EDIT-REV-` plus that receipt, against the sale's original store

A sale edit restocks every old line, deletes those lines, then deducts the new lines. The net on-hand is consistent because both steps share one transaction. The ledger still shows a full reversal and a new sale when a line did not change. Void is allowed only while status is `completed`. A second void is HTTP 422 `Only completed sales can be voided`. Receiving is allowed only from `ordered`. A second receive is HTTP 422 `Cannot change status of received order`, so stock is added once. Cancelling an order does not move stock.

Commerce import uses the same `sale` movement. Before that, `createSaleFromImport` rejects a product whose `storeId` is not the connection store. POS does not do that check: a sale deducts the sale's store, which can differ from `product.storeId`. The import movement's `userId` is the acting member from `CommerceIntegrationActingUserResolver`: first active `PROPRIETAIRE`, otherwise `GESTIONNAIRE`, otherwise the earliest other active member. POS and PO movements store the caller's user id.

## Low stock

`isLowStock()` is `quantity <= minStock`. A threshold of 0 flags only quantity 0. Products with no row are missing from both the stock list and the alerts.

`StockLevelResponse.lowStock` is returned by the stock API on every plan. Dashboard alerts are a separate gate:

- Shown when the business has no plan, or `feature_stock_alerts` is true. `V33` sets starter to false, pro and business to true.
- When the flag is false, the dashboard preview and `GET /api/v1/dashboard/low-stock-items` return an empty list. They do not return 403.
- The dashboard check is any of `SALES_READ`, `PRODUCTS_READ`, or `REPORTS_READ`. `STOCK_READ` is not required.
- The scan uses `StoreRepository.findByBusinessId`, so inactive stores are included. Assignment is not applied. A `storeId` outside that list yields an empty slice and no 404. Inactive products are included. A missing product name is returned as `Unknown`.
- The dashboard payload keeps a preview of 10 rows, sorted by store name then product name. The slice route defaults to page 0, size 10, and caps size at 50.

## Who can call it

`RolePermissionService` loads permission codes for the caller's `roleId`. A platform admin skips that check, and still needs `hasBusinessAccess()`. That test runs first and returns HTTP 403 `Business context required` when the JWT has no business.

Default system roles from `V54` and `BusinessRoleBootstrapService`: proprietaire has `STOCK_READ`, `STOCK_INIT`, and `STOCK_ADJUST`. Gestionnaire has the same three (the only permissions withheld are `SUBSCRIPTION_UPDATE` and `BUSINESS_USERS_DELETE`). Caissier has `STOCK_READ` only. Sale create and void still move stock, because they check `SALES_CREATE` and `SALES_DELETE`. Sale edit checks `SALES_UPDATE`: the `V54` backfill granted that to existing caissiers, and `BusinessRoleBootstrapService` does not grant it to caissiers created later.

No stock method writes an audit log.

## Schema

Product delete deactivates the product and leaves the stock row in place, so search and low-stock scans still see it. Store delete is a hard delete. `V1` references `product` and `store` from both stock tables with the default `NO ACTION`. Deleting a store that still has stock or movements fails that foreign key, and the generic handler returns HTTP 500.

Neither stock table has `business_id`. The HTTP methods check the product and the store. The sale and purchase helpers do not repeat those checks.

## Codepaths

- `inventory/infrastructure/web/StockController.java`
- `inventory/application/service/StockService.java`
- `inventory/application/dto/StockInitRequest.java`, `StockAdjustmentRequest.java`, `StockLevelResponse.java`, `StockMovementResponse.java`
- `inventory/domain/model/ProductStoreStock.java`, `StockMovement.java`
- `inventory/domain/repository/ProductStoreStockRepository.java`, `StockMovementRepository.java`
- `sales/application/service/SaleService.java` (`createSale`, `updateSale`, `voidSale`, `createSaleFromImport`)
- `supplier/application/service/PurchaseOrderService.java` (`updateStatus`) and `supplier/domain/model/PurchaseOrder.java` (`transitionTo`)
- `integration/commerce/application/service/CommerceIntegrationActingUserResolver.java`
- `analytics/application/service/DashboardService.java` (`buildLowStockItems`, `sliceLowStockItems`) and `analytics/infrastructure/web/DashboardController.java`
- `identity/application/service/RolePermissionService.java`
- `tenant/application/service/BusinessRoleBootstrapService.java`
- `shared/infrastructure/web/GlobalExceptionHandler.java`, `ApiConstants.java`
- `src/main/resources/db/migration/V1__initial_schema.sql`, `V33__add_plan_retention_features.sql`, `V54__business_roles_permissions.sql`
