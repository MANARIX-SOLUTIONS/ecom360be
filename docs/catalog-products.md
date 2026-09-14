# Catalog products and categories

Source-backed guide for tenant product/category CRUD, plan `maxProducts`, SKU
rules, store coupling, and how catalog rows interact with stock, POS, and
commerce import.

Verified against `ProductController`, `ProductService`, `Product` /
`ProductRequest` / `ProductRepository`, `CategoryController`, `CategoryService`,
`Category` / `CategoryRequest` / `CategoryRepository`, `CachedLookups`,
`CacheConfig`, `SaleService.createSale` / `createSaleFromImport`,
`CommerceOrderIngestionService.resolveProduct`, `StockService.initializeStock`,
`Plan.isUnlimited` + `V32` plan seeds, `NavigationPermissionRules`,
`BusinessRoleBootstrapService`, `V1__initial_schema.sql` (`product` /
`category`), and `application.yml` (`spring.jpa.hibernate.ddl-auto`).

Image upload/serve is documented separately (open draft
`docs/product-images.md` on PR #77). This page covers the catalog entity
workflow those uploads attach to.

## Intent

- Keep a per-business catalog of sellable items, each owned by **one**
  `storeId`.
- Group products with business-wide categories (name + color + sort order).
- Enforce plan `maxProducts` on **create** of active products.
- Feed POS line prices from `Product.salePrice` (not from the sale request).
- Resolve commerce webhook lines by `productId` or store-scoped normalized SKU.

Creating a product does **not** create `product_store_stock`. POS and purchase
receipts lazy-create a stock row at quantity 0; an explicit `POST /api/v1/stock/init`
is required before the first sale if you need a starting quantity.

## Public surface

JWT + business context required. Base paths: `/api/v1/products`,
`/api/v1/categories`.

### Products

| Method | Path | Permission | Controller `@PreAuthorize` |
|--------|------|------------|----------------------------|
| `POST` | `/products` | `PRODUCTS_CREATE` | Yes (`PRODUCTS_CREATE` or `PLATFORM_ADMIN`) |
| `GET` | `/products/{id}` | `PRODUCTS_READ` | No — service `RolePermissionService.require` |
| `GET` | `/products` | `PRODUCTS_READ` | No — service check |
| `PUT` | `/products/{id}` | `PRODUCTS_UPDATE` | No — service check |
| `POST` | `/products/{id}/image/upload` | `PRODUCTS_UPDATE` | Yes (`PRODUCTS_UPDATE` or `PLATFORM_ADMIN`) |
| `DELETE` | `/products/{id}` | `PRODUCTS_DELETE` | No — service check |

List query params: `page` (default 0), `size` (default 20, capped at 100),
`search` (optional), `storeId` (optional). Sort is `createdAt` descending.

Nav key `products` requires `PRODUCTS_READ`.

### Categories

| Method | Path | Permission |
|--------|------|------------|
| `POST` | `/categories` | `CATEGORIES_CREATE` |
| `GET` | `/categories` | `CATEGORIES_READ` |
| `GET` | `/categories/{id}` | `CATEGORIES_READ` |
| `PUT` | `/categories/{id}` | `CATEGORIES_UPDATE` |
| `DELETE` | `/categories/{id}` | `CATEGORIES_DELETE` |

`CategoryController` has **no** method-level `@PreAuthorize`. All checks are in
`CategoryService`. List is **not** paginated; it returns the full cached list
ordered by `sortOrder` ascending.

Default cashier role (`BusinessRoleBootstrapService`) gets `PRODUCTS_READ` and
`CATEGORIES_READ` only — no product/category writes.

## Plan / permission gates

| Action | Permission | `maxProducts` |
|--------|------------|---------------|
| Create product | `PRODUCTS_CREATE` | Count of **active** products vs plan limit |
| Update / delete / get / list product | matching `PRODUCTS_*` | **Not** checked |
| Category CRUD | matching `CATEGORIES_*` | **Not** checked (no category cap) |

`Plan.isUnlimited(limit)` is `limit == 0`. Seeded `V32` values:

| Plan slug | `maxProducts` |
|-----------|----------------|
| `starter` | 100 |
| `pro` | 500 |
| `business` | 0 (unlimited) |

Gate uses `getPlanForBusiness(...).ifPresent(...)`:

- **No access-granting subscription** → create is **not** blocked by the cap.
- Rejected create message: *« Limite du plan atteinte : maximum N produit(s). Passez à un plan supérieur. »*

Soft-delete (`isActive = false`) frees a plan slot. The SKU of that row still
blocks reuse (see pitfalls).

Platform admins bypass RBAC (`RolePermissionService.can`). Catalog rows still
use `UserPrincipal.businessId` from the JWT (there is no `X-Business-Id`
override). A backoffice token with `businessId = null` should not call these
routes.

## Create / update product

Request body (`ProductRequest`):

```json
{
  "name": "Coca-Cola 33cl",
  "sku": "SKU-001",
  "barcode": "5449000000996",
  "description": "Canette",
  "costPrice": 150,
  "salePrice": 250,
  "unit": "pièce",
  "imageUrl": null,
  "categoryId": "d0000001-0000-4000-8000-000000000001",
  "isActive": true,
  "storeId": "00000000-0000-0000-0000-000000000002"
}
```

| Field | Constraint |
|-------|------------|
| `name` | required, max 255 |
| `sku` | optional, max 100 |
| `barcode` | optional, max 100; **no uniqueness check** |
| `costPrice` / `salePrice` | required integers `>= 0` |
| `unit` | optional; compact constructor defaults to `pièce` |
| `imageUrl` | optional, DTO max 500 (matches Flyway `VARCHAR(500)`) |
| `categoryId` | optional; must belong to the same business if set |
| `isActive` | required on the DTO; null is rewritten to `true` |
| `storeId` | **required** on create; must belong to the business |

Prices are integers (same convention as sales/expenses; typically XOF with no
fractional part). POS `SaleService.createSale` rejects `salePrice == null` or
`<= 0` at sale time even though catalog create allows `0`.

`PUT` may change `storeId`. Existing `product_store_stock` rows for the previous
store are **not** moved or deleted.

Audit (`AuditLogService.logAsync`): `CREATE` / `UPDATE` / `DELETE` on entity
type `Product`. Categories have **no** audit writers.

## List and search

- **No `search`**: `findByBusinessIdAndIsActive(..., true)` (or the store-scoped
  equivalent). Soft-deleted products are hidden.
- **With `search`**: `LOWER(name|sku|barcode) LIKE %term%` — **does not** filter
  `isActive`, so inactive SKUs reappear in search results.
- **`storeId` omitted**: all products for the business, **including other
  stores**. `ProductService` does **not** apply `CachedLookups.storesForUser`.
- **`storeId` set**: 404 if the store is missing or not in this business.

`GET /products/{id}` uses `findByBusinessIdAndId` with **no** `isActive` filter,
so a soft-deleted product remains readable (and can be `PUT` back to
`isActive: true`).

## Delete

`DELETE /products/{id}` is a **soft** delete:

1. Audit `DELETE`.
2. Delete the managed image file if `imageUrl` is a catalog upload path.
3. Set `isActive = false` and `imageUrl = null`.
4. Leave stock rows and sale lines untouched.

There is no hard-delete path on the API.

## Categories

Request body (`CategoryRequest`):

```json
{
  "name": "Boissons",
  "color": "#2563eb",
  "sortOrder": 10
}
```

- `name` required, max 255. Uniqueness is `existsByBusinessIdAndName` (exact
  string; no DB unique constraint).
- `color` optional, max 20.
- `sortOrder` optional; defaults to `0`.
- Delete is a **hard** `repository.delete`. It is rejected when
  `countByBusinessIdAndCategoryIdAndIsActive(..., true) > 0`:
  *« Impossible de supprimer cette catégorie : N produit(s) l'utilisent. »*
  Inactive products on that category do **not** block delete.
- List reads `CachedLookups.categoriesByBusiness` (`@Cacheable("categories")`,
  Caffeine 10 min / 1000 entries in `CacheConfig`). Create/update/delete call
  `evictCategories(businessId)`. `GET /categories/{id}` always hits the
  database.

## SKU: catalog vs commerce

| Path | Lookup |
|------|--------|
| Catalog create/update | `existsByBusinessIdAndSku` — **business-wide**, exact string, includes inactive rows. Blank SKU skips the check. |
| Commerce import | `findByBusinessIdAndStoreIdAndSkuNormalized` — **store-scoped**, `LOWER(TRIM(sku))`, active products only |

There is **no** unique index on `product.sku` in Flyway. `SKU-001` and
`sku-001` can both be created; commerce import then matches them as the same
SKU inside one store.

Commerce `productId` must also `belongsToStore` the connection's store and be
active. POS `createSale` looks up by `businessId + productId` only — it does
**not** require `product.storeId == sale.storeId`. Stock movements for the sale
use the **sale** `storeId`.

## Schema pitfall (`store_id`)

Flyway `V1__initial_schema.sql` creates `product` **without** `store_id`. The
JPA entity maps `storeId` as `nullable = false`. No later migration adds the
column.

Runtime environments that boot with `spring.jpa.hibernate.ddl-auto: update`
(base `application.yml`; **not** overridden in `application-prod.yml`) get the
column from Hibernate. `application-prod.yml` sets `spring.flyway.enabled:
false`, so production schema for this column is not owned by Flyway.

Seed `V11__seed_product.sql` inserts catalog rows without `store_id`. After
Hibernate adds the column, those rows need a backfill before `NOT NULL` writes
succeed.

## Common pitfalls

- Product create does not initialize stock. First POS sale against a missing
  stock row starts at quantity 0 and fails with *« Stock insuffisant… »*
  unless you `POST /stock/init` (or receive a purchase order) first.
- Plan cap counts **active** products; SKU uniqueness includes **inactive**
  products. Soft-delete frees a slot you cannot refill with the same SKU.
- List-without-search hides inactive rows; search shows them.
- Catalog list is not filtered by the caller's store assignments.
- Moving a product to another store does not move stock.
- POS can sell a product whose `storeId` differs from the ticket store; commerce
  import cannot.
- `barcode` is not unique. Duplicate barcodes are allowed.
- Category list can stay stale up to the Caffeine TTL if eviction is skipped
  (direct SQL, or a code path that does not call `evictCategories`).
- Controller `@PreAuthorize` is only on product create and image upload. Other
  catalog mutations rely on the service-layer permission check.

## Implementation map

| Concern | Code |
|---------|------|
| HTTP | `catalog/infrastructure/web/ProductController`, `CategoryController` |
| Rules | `catalog/application/service/ProductService`, `CategoryService` |
| Persistence | `catalog/domain/model/Product`, `Category` + repositories |
| Category cache | `shared/infrastructure/cache/CachedLookups`, `CacheConfig` |
| Plan cap | `ProductService.create` + `SubscriptionService.getPlanForBusiness` + `Plan.isUnlimited` |
| POS price | `sales/application/service/SaleService.createSale` (`prod.getSalePrice()`) |
| Commerce SKU | `CommerceOrderIngestionService.resolveProduct` |
| Stock init | `inventory/application/service/StockService.initializeStock` |
| Images | `ProductImageStorageService` / `PublicProductImageController` (see PR #77) |
| Permissions | `Permission.PRODUCTS_*` / `CATEGORIES_*`, `NavigationPermissionRules.products` |
