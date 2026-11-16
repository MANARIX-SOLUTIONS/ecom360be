# Delivery couriers

Couriers (`livreur`) and delivery records (`livraison`) are performance logs for a business. Recording a delivery does not change the sale, the stock, or the client's balance. There is no plan quota on how many couriers or rows a business can store.

The tenant is the JWT `businessId`. `X-Business-Id` is not read. Couriers are not tied to a store, and `business_user_store` does not filter these routes.

## Routes

All paths are under `/api/v1/delivery` and require a JWT. Creates return HTTP 201. Delete returns HTTP 204. Reads and courier update return HTTP 200.

| Method | Path | Permission | Result |
| --- | --- | --- | --- |
| `GET` | `/couriers?activeOnly=false` | `DELIVERY_COURIERS_READ` | Every courier, `name` ascending. `activeOnly=true` keeps `isActive=true` only |
| `GET` | `/couriers/stats` | `DELIVERY_COURIERS_READ` | One stats row per courier, including inactive, in the same name order |
| `GET` | `/couriers/{id}` | `DELIVERY_COURIERS_READ` | Inactive couriers are returned. HTTP 404 `Livreur not found with id: {id}` otherwise |
| `GET` | `/couriers/{id}/stats` | `DELIVERY_COURIERS_READ` | Stats for that courier. Same 404 when the courier is outside the business |
| `POST` | `/couriers` | `DELIVERY_COURIERS_CREATE` | Create |
| `PUT` | `/couriers/{id}` | `DELIVERY_COURIERS_UPDATE` | Replace name, phone, email, and `isActive` |
| `DELETE` | `/couriers/{id}` | `DELIVERY_COURIERS_DELETE` | Delete the courier |
| `POST` | `/deliveries` | `DELIVERY_COURIERS_CREATE` | Insert one immutable delivery row |
| `GET` | `/deliveries?page=0&size=20` | `DELIVERY_COURIERS_READ` | Business list, `createdAt` descending |
| `GET` | `/deliveries?courierId={id}` | `DELIVERY_COURIERS_READ` | That courier only, `deliveredAt` descending |

There is no get, update, or delete for a delivery row. Status cannot be corrected after insert.

Delivery lists are a JSON array, not a page object. There is no total count. `page` defaults to 0 and `size` to 20. The business list caps `size` at 100. The courier list caps `size` at 50. `page` below 0 or `size` below 1 is HTTP 400. A page past the end is HTTP 200 `[]`.

The courier list is not paged.

A courier id from another business is HTTP 404 on every route that takes `{id}` or `courierId`. The business-wide delivery list does not 404.

## Access

Checks run in this order: business context, permission, then the plan flag.

`PROPRIETAIRE` and `GESTIONNAIRE` receive all four `DELIVERY_COURIERS_*` codes, both from `V54__business_roles_permissions.sql` and from `BusinessRoleBootstrapService`. `CAISSIER` receives none of them on either path. A cashier call fails at the permission check with HTTP 403 `Accès refusé : permission DELIVERY_COURIERS_* requise pour votre rôle`, including on a Pro plan.

The `livreurs` navigation entry is visible when the caller has any one of the four codes. List, get, and stats still require `DELIVERY_COURIERS_READ`. A create-only custom role can see the menu and receive HTTP 403 on those reads.

A platform-admin principal skips the permission check. The plan flag still runs when the JWT has a `businessId`.

An expired or missing access-granting subscription is HTTP 402 from `SubscriptionRequiredFilter`, before the controller:

```json
{
  "code": "SUBSCRIPTION_REQUIRED",
  "message": "Votre période d'essai est terminée. Veuillez souscrire à un plan pour continuer."
}
```

When a plan row is loaded, `feature_delivery_couriers` must be true. Seed data sets it false on `starter` and true on `pro` and `business` (`V51__delivery_couriers_and_plan_feature.sql`). Any other plan keeps the column default, false. The HTTP 403 detail is `Gestion des livreurs non incluse dans votre plan. Passez au plan Pro ou Business.` on courier routes, and `Gestion des livreurs non incluse dans votre plan.` on delivery routes. A granting subscription whose plan row cannot be loaded skips this flag.

## Couriers

```json
{
  "name": "Moussa Ndiaye",
  "phone": "+221771234567",
  "email": "moussa@example.com",
  "isActive": true
}
```

`name` is required, not trimmed, and at most 255 characters. Phone is at most 50 characters. Email is at most 255 characters. Neither phone nor email is format-checked, and neither is unique. Omitted `isActive`, or `isActive: null`, is stored as `true`. A PUT that omits `isActive` reactivates the courier. Phone and email may be cleared with null or `""`.

Create rejects a case-insensitive duplicate name in the same business with HTTP 409 `Livreur already exists: {name}`. There is no unique index, so two concurrent creates can both insert, and PUT never runs this check. PUT can rename a courier onto a name that already exists.

Delete removes the `livreur` row. `livraison.courier_id` is `ON DELETE CASCADE`, so every delivery for that courier is removed in the same database statement. The API does not warn and does not write an audit row.

An inactive courier still accepts new deliveries. Stats and `GET /couriers/{id}` include inactive couriers. Only `GET /couriers?activeOnly=true` hides them.

## Deliveries

```json
{
  "courierId": "4e42d9d7-237c-4ec3-89f8-bb13df927544",
  "saleId": "8622d7df-dfe4-4c0c-a0f5-4059dd6c29f3",
  "status": "delivered",
  "parcelsCount": 2,
  "notes": "Left at the shop"
}
```

`courierId` and `status` are required. The courier must already belong to the JWT business. `status` is case-sensitive and must be `delivered`, `failed`, or `cancelled`. Anything else is HTTP 400 `Invalid status: {value}` (title `Bad Request`, no field-error map). The same three values are the database check constraint.

`parcelsCount` below 1, including an omitted field, is stored as 1 and the request still returns HTTP 201. The column check is `parcels_count > 0`. A JSON `null` cannot bind the primitive and falls through to HTTP 500 `An unexpected error occurred`.

`saleId` may be omitted. When it is present it must be an existing `sale.id`. An unknown id hits the foreign key and the generic handler returns HTTP 500. The sale is not required to belong to this business, store, or status. The same sale id can appear on many delivery rows. Voiding a sale does not touch `livraison`. If the sale row itself is deleted, `sale_id` is set to null (`ON DELETE SET NULL`).

`deliveredAt` is `Instant.now()` only when `status` is `delivered`. Failed and cancelled rows leave it null. `notes` is optional and has no length cap in the API.

The courier-scoped list sorts by `delivered_at` descending. PostgreSQL orders nulls first on that sort, so failed and cancelled rows come before delivered rows. The business list sorts by `created_at` descending, which is always set.

Posting the same body twice inserts two rows. Both count in stats.

## Stats

`CourierStatsResponse` fields:

| Field | Meaning |
| --- | --- |
| `totalParcelsDelivered` | Sum of `parcels_count` where `status = delivered` |
| `totalDeliveries` | Count of `delivered` rows, not parcels |
| `failedDeliveries` | Count of `failed` rows |
| `successRatePercent` | `100 * deliveredRows / (deliveredRows + failedRows)` |

The rate is a raw double. It is not rounded and it is not weighted by parcels. Failed parcels are excluded from `totalParcelsDelivered`. `cancelled` rows are excluded from every field. A courier with only cancellations, or with no rows, returns `0`, `0`, `0`, and `100`.

`GET /couriers/stats` still returns a row for a courier who has no deliveries.

## Side effects

Courier and delivery methods write only `livreur` and `livraison`. `audit_log`, stock movements, and the sale row stay as they were. Store assignment is not consulted.

## Codepaths

- `delivery/infrastructure/web/CourierController.java`
- `delivery/infrastructure/web/DeliveryController.java`
- `delivery/application/service/CourierService.java`
- `delivery/application/service/DeliveryService.java`
- `delivery/application/dto/CourierRequest.java`, `DeliveryRequest.java`, `CourierStatsResponse.java`
- `delivery/domain/repository/CourierRepository.java`, `DeliveryRepository.java`
- `identity/application/service/RolePermissionService.java`
- `identity/application/NavigationPermissionRules.java` (`livreurs`)
- `tenant/application/service/BusinessRoleBootstrapService.java`
- `tenant/application/service/SubscriptionService.java` (`getPlanForBusiness`)
- `tenant/infrastructure/security/SubscriptionRequiredFilter.java`
- `src/main/resources/db/migration/V51__delivery_couriers_and_plan_feature.sql`
- `src/main/resources/db/migration/V52__delivery_livraison.sql`
- `src/main/resources/db/migration/V54__business_roles_permissions.sql`
