# Delivery and team workflows

This page covers two related tenant workflows:

1. assigning team members to stores;
2. managing couriers and recording deliveries.

Primary codepaths:

- `tenant/infrastructure/web/BusinessUserController.java`
- `tenant/application/service/BusinessUserService.java`
- `delivery/infrastructure/web/CourierController.java`
- `delivery/infrastructure/web/DeliveryController.java`
- `delivery/application/service/CourierService.java`
- `delivery/application/service/DeliveryService.java`
- `src/main/resources/db/migration/V49__business_user_store_assignment.sql`
- `src/main/resources/db/migration/V51__delivery_couriers_and_plan_feature.sql`
- `src/main/resources/db/migration/V52__delivery_livraison.sql`

## Business users and store assignment

Business users are tenant members. They have:

- a `business_role` that controls permissions;
- zero or more assigned stores through `business_user_store`.

Routes:

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/business/users` | `BUSINESS_USERS_READ` | List active business users |
| `POST` | `/api/v1/business/users` | `BUSINESS_USERS_CREATE` | Invite a user |
| `PUT` | `/api/v1/business/users/{id}/stores` | `BUSINESS_USERS_UPDATE` | Replace assigned stores |
| `GET` | `/api/v1/business/users/{id}/stores` | `BUSINESS_USERS_READ` | List assigned stores |

Store assignment request:

```json
{
  "storeIds": [
    "0c72c9d6-ec4b-4b6f-a1af-68776cc320d7",
    "3a4f7e0d-cd17-4e52-8a16-2c067f7b0a76"
  ]
}
```

`PUT /business/users/{id}/stores` replaces all assignments for the business
user. Passing an empty list clears all assignments.

The service validates that:

- the target business user belongs to the current business;
- every store id belongs to the current business.

## Invite constraints

Inviting users is constrained by plan limits and feature flags:

- `max_users` limits active business users unless the plan value is `0`
  (unlimited);
- if `feature_role_management` is false, only `CAISSIER` users can be invited.

New users receive an invitation email with a password reset link. In local
development, inspect the message in Maildev.

## Delivery concepts

The delivery module uses French domain table names:

| Domain | Table | Description |
| --- | --- | --- |
| Courier | `livreur` | Person delivering parcels for a business |
| Delivery | `livraison` | Delivery result and parcel count for a courier |

The delivery module requires both:

- a plan with `feature_delivery_couriers = true` (`pro` and `business` in seed
  data);
- the relevant `DELIVERY_COURIERS_*` permission.

## Courier API

Routes:

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/delivery/couriers?activeOnly=false` | `DELIVERY_COURIERS_READ` | List couriers |
| `GET` | `/api/v1/delivery/couriers/stats` | `DELIVERY_COURIERS_READ` | Stats for all couriers |
| `GET` | `/api/v1/delivery/couriers/{id}` | `DELIVERY_COURIERS_READ` | Courier detail |
| `GET` | `/api/v1/delivery/couriers/{id}/stats` | `DELIVERY_COURIERS_READ` | One courier's stats |
| `POST` | `/api/v1/delivery/couriers` | `DELIVERY_COURIERS_CREATE` | Create courier |
| `PUT` | `/api/v1/delivery/couriers/{id}` | `DELIVERY_COURIERS_UPDATE` | Update courier |
| `DELETE` | `/api/v1/delivery/couriers/{id}` | `DELIVERY_COURIERS_DELETE` | Delete courier |

Create/update payload:

```json
{
  "name": "Moussa Ndiaye",
  "phone": "+221771234567",
  "email": "moussa@example.com",
  "isActive": true
}
```

Courier names are unique per business case-insensitively.

## Delivery API

Routes:

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/delivery/deliveries` | `DELIVERY_COURIERS_CREATE` | Record a delivery |
| `GET` | `/api/v1/delivery/deliveries?page=0&size=20` | `DELIVERY_COURIERS_READ` | List business deliveries |
| `GET` | `/api/v1/delivery/deliveries?courierId={id}` | `DELIVERY_COURIERS_READ` | List deliveries for one courier |

Create payload:

```json
{
  "courierId": "4e42d9d7-237c-4ec3-89f8-bb13df927544",
  "saleId": "8622d7df-dfe4-4c0c-a0f5-4059dd6c29f3",
  "status": "delivered",
  "parcelsCount": 2,
  "notes": "Delivered to customer"
}
```

Constraints:

- `courierId` is required and must belong to the current business.
- `saleId` is optional.
- `status` must be `delivered`, `failed`, or `cancelled`.
- `parcelsCount` defaults to 1 when less than 1 is supplied.
- `deliveredAt` is set automatically only when status is `delivered`.
- list page sizes are capped at 50 for courier-specific lists and 100 for
  business-wide lists.

## Courier stats

Stats include:

- `totalParcels`;
- delivered count;
- failed count;
- success rate percent.

Only `delivered` and `failed` count toward completed deliveries. A courier with
no completed deliveries reports a 100% success rate by current service logic.

## Common pitfalls

- Delivery permissions alone are insufficient when the plan does not include
  `feature_delivery_couriers`.
- Store assignment is replace-all, not patch/additive.
- Delivery records are scoped by business and courier; a courier id from another
  business is treated as not found.
- `DELIVERY_COURIERS_CREATE` is used for recording deliveries as well as
  creating couriers.
