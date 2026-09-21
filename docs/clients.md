# Client directory

Source-backed guide for tenant client records: create, list, get, update,
delete, plan `maxClients`, and how a client is attached to a POS sale.

Verified against `ClientController`, `ClientService`, `Client` / `ClientRequest` /
`ClientResponse` / `ClientRepository`, `ClientPayment` / `ClientPaymentRequest`,
`Plan.isUnlimited` and `V32__update_plans_plans_abonnements.sql`,
`NavigationPermissionRules`, `BusinessRoleBootstrapService`,
`V54__business_roles_permissions.sql` (cashier seed), `SaleService.createSale` /
`createSaleFromImport` / `requireClientForPosSale`, `DashboardService`
receivable queries, `SubscriptionRequiredFilter`, `SubscriptionService.getPlanForBusiness`,
`RolePermissionService`, `GlobalExceptionHandler`, and `V1__initial_schema.sql`
(`client`, `client_payment`, `sale.client_id`).

Credit **sales** (how `paymentMethod = credit` changes `creditBalance` on sale
update, and the dashboard receivable formula) are a separate workflow. This
page covers the client row those flows point at, plus the repayment endpoints
that live on the same controller.

## Intent

- Keep a per-business address book of customers. A client has no `storeId`.
- Give POS a required `clientId` that already exists in the same business.
- Cap how many client rows a plan may store (`maxClients`), counted on create.
- Expose `creditBalance` on the client response. Create and update do not set it.

Commerce order import does not create or attach a client. `createSaleFromImport`
persists `sale.clientId = null`.

## Public surface

JWT required. Base path: `/api/v1/clients`.

`ClientController` has no method-level `@PreAuthorize`. Every method calls
`RolePermissionService.require` after `requireBiz`.

| Method | Path | Permission | Success |
|--------|------|------------|---------|
| `POST` | `/clients` | `CLIENTS_CREATE` | 201 `ClientResponse` |
| `GET` | `/clients` | `CLIENTS_READ` | 200 page |
| `GET` | `/clients/{id}` | `CLIENTS_READ` | 200 `ClientResponse` |
| `PUT` | `/clients/{id}` | `CLIENTS_UPDATE` | 200 `ClientResponse` |
| `DELETE` | `/clients/{id}` | `CLIENTS_DELETE` | 204 |
| `POST` | `/clients/{id}/payments` | `CLIENTS_UPDATE` | 201 `ClientPaymentResponse` |
| `GET` | `/clients/{id}/payments` | `CLIENTS_READ` | 200 page |

List query params: `page` (default 0), `size` (default 20, capped at
`ApiConstants.MAX_PAGE_SIZE` = 100), `search` (optional). Sort is `createdAt`
descending. Payment list uses the same page defaults and the same cap of 100,
ordered by `createdAt` descending.

Nav key `clients` requires `CLIENTS_READ`.

Tenant scope is `UserPrincipal.businessId()` from the JWT. `ApiConstants.X_BUSINESS_ID`
is unused. `requireBiz` returns 403 `Business context required` when the
principal has no business id and is not a platform admin. A platform admin
bypasses permission codes in `RolePermissionService.can`, but lookups still use
`businessId`.

`SubscriptionRequiredFilter` returns HTTP 402 `SUBSCRIPTION_REQUIRED` for
`/api/v1/clients/**` when the business has no access-granting subscription whose
`currentPeriodEnd` is today or later.

## Request and response

`ClientRequest`:

| Field | Constraint | Notes |
|-------|------------|--------|
| `name` | `@NotBlank`, max 255 | Required. Matches `VARCHAR(255)`. |
| `phone` | max 50, optional | Matches `VARCHAR(50)`. No format check. |
| `email` | max 255, optional | Not `@Email`. No uniqueness check. |
| `address` | max 500, optional | Bean validation only. Column is `TEXT`. |
| `notes` | max 1000, optional | Bean validation only. Column is `TEXT`. Not included in search. |
| `isActive` | optional boolean | `null` (omitted) becomes `true` in the compact constructor. |

There is no phone, email, or name uniqueness in the service or in Flyway
(`client` has no unique index beyond the primary key).

`ClientResponse` adds `id`, `businessId`, `creditBalance`, `createdAt`,
`updatedAt`. `creditBalance` is not part of `ClientRequest`. Create leaves the
Java default `0` (column default is also `0`). Update does not change it.

`PUT` replaces every writable field. It is not a patch. Sending a body without
`isActive` reactivates the client.

Validation failures are HTTP 400 Problem Details (`MethodArgumentNotValidException`).
Unknown id in this business is HTTP 404 `Resource Not Found`. Missing permission
is HTTP 403 with `Accès refusé : permission CLIENTS_* requise pour votre rôle`.
Plan-limit failures are HTTP 422 `Business Rule Violation`.

## Plan limit

Checked only in `ClientService.create`, and only when
`SubscriptionService.getPlanForBusiness` returns a plan (`ifPresent`). A request
that reaches the service with no plan row does not apply `maxClients`. The 402
filter above is what blocks a business with no current subscription.

`Plan.isUnlimited(limit)` is `limit == 0`. The count is
`ClientRepository.countByBusinessId`: **every row**, active and inactive.

| Plan slug (`V32`) | `max_clients` | Meaning on create |
|-------------------|---------------|-------------------|
| `starter` | 50 | 422 once 50 rows exist |
| `pro` | 0 | unlimited |
| `business` | 0 | unlimited |

Update, get, list, and delete do not consult `maxClients`. Deactivating a
client (`isActive: false`) does **not** free a starter slot. A successful hard
delete does, because the row is gone.

`feature_client_credits` is false on `starter` and true on `pro` and `business`.
That flag is **not** required to create a client. It gates `POST /clients/{id}/payments`
only (see below).

## List visibility

| Call | Who is returned |
|------|-----------------|
| `GET /clients` with blank or omitted `search` | `isActive = true` for this business |
| `GET /clients?search=` | still active only; `search` is trimmed |
| `GET /clients?search=awa` | active rows whose name, phone, email, or address contains the query, case-insensitive `LIKE` |
| `GET /clients/{id}` | the row even when inactive, if `businessId` matches |

Inactive clients disappear from the directory and from search. They remain
readable by id and can still be attached to a POS sale (next section).

## Who can write

Default system roles (`BusinessRoleBootstrapService`, and the V54 backfill for
`CLIENTS_*`):

| Role code | Client permissions |
|-----------|--------------------|
| `PROPRIETAIRE` | all `CLIENTS_*` |
| `GESTIONNAIRE` | all `CLIENTS_*` (manager exclusions are `SUBSCRIPTION_UPDATE` and `BUSINESS_USERS_DELETE` only) |
| `CAISSIER` | `CLIENTS_READ` only |

A cashier can open the clients screen, list, and fetch a client (and list
payments). Create, update, delete, and record payment return 403. Custom roles
follow whatever `CLIENTS_*` codes are linked; the service does not special-case
role names.

## POS and commerce

`POST /api/v1/sales` (`SaleRequest.clientId` is `@NotNull`) calls
`requireClientForPosSale`:

- `clientId == null` → 422 `Client obligatoire pour cette vente.`
- no row for `(businessId, clientId)` → 404 `Client`
- `isActive` is **not** checked

`createSale` rejects `paymentMethod = credit` before that lookup, so a normal
POS create does not call `Client.addCredit`. Import sales pass `clientId` null
and skip the lookup.

Sale update uses the same existence check. Whether an update may add credit is
outside this page.

## Delete

`delete` is a hard `clientRepo.delete`. There is no soft-delete flag flip on
this path (use `PUT` with `isActive: false` to hide a client).

Flyway foreign keys, both default `NO ACTION` (no `ON DELETE CASCADE`):

- `sale.client_id` → `client(id)` (nullable)
- `client_payment.client_id` → `client(id)` (not null)

Deleting a client that still has sales or payments raises a database integrity
error. `GlobalExceptionHandler` has no `DataIntegrityViolationException`
handler, so the API responds **500** `An unexpected error occurred`. Deactivate
the client, or remove dependent rows, before deleting.

No client create/update/delete path writes an audit event.

## Repayments (same controller)

`POST /api/v1/clients/{id}/payments` body (`ClientPaymentRequest`):

```json
{
  "storeId": "00000000-0000-0000-0000-000000000001",
  "amount": 5000,
  "paymentMethod": "cash",
  "note": "Acompte"
}
```

- `storeId` required. The service does **not** check that the store belongs to
  the business.
- `amount` required integer, `@Min(1)`.
- `paymentMethod` `@NotBlank`. No enum.
- `note` optional.

When a plan is present, `featureClientCredits` must be true or the call is 422
`Crédits clients non inclus dans votre plan. Passez à un plan supérieur.`
Starter fails that check. With no plan row, `ifPresent` skips it.

The handler loads the client (404 if missing), calls `deductCredit(amount)`
with **no floor**, then inserts `client_payment` (`userId` from the principal).
`creditBalance` can become negative. This method is `@Transactional`; the other
client methods are not (each is a single save).

`GET /clients/{id}/payments` requires the client to exist in the business, then
returns that client's payments. It does not filter by store.

Dashboard debtor count and receivable sum
(`countDebtorsWithPositiveBalance`, `sumPositiveCreditBalance`) include only
**active** clients with `creditBalance > 0`. Deactivating a debtor removes them
from those figures without changing the stored balance. A negative balance is
excluded from the sum.

## Example

Create (owner or manager):

```http
POST /api/v1/clients
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "name": "Awa Diallo",
  "phone": "771234567",
  "email": "awa@example.com",
  "address": "Dakar",
  "notes": "Préfère le retrait boutique",
  "isActive": true
}
```

Then pass the returned `id` as `clientId` on `POST /api/v1/sales`.

## Pitfalls

- Starter capacity counts inactive rows. Hide-via-`isActive` does not free a slot; delete does, until a sale or payment blocks it with a 500.
- Omitted `isActive` on create or update means active. A partial update body turns an inactive client back on.
- Search never returns inactive clients and never matches `notes`.
- Duplicate phone or email is allowed.
- POS accepts an inactive client id. Import sales have no client.
- Payment `storeId` is not tenant-checked, and repayment can drive `creditBalance` below zero.
- Cashiers have read access only, including the clients nav entry.

## Implementation map

| Concern | Code |
|---------|------|
| HTTP | `client/infrastructure/web/ClientController.java` |
| Rules | `client/application/service/ClientService.java` |
| Row | `client/domain/model/Client.java`, `V1__initial_schema.sql` |
| Search / counts | `client/domain/repository/ClientRepository.java` |
| POS attach | `sales/application/service/SaleService.java` (`requireClientForPosSale`, `createSaleFromImport`) |
| Plan numbers | `tenant/domain/model/Plan.java`, `V32__update_plans_plans_abonnements.sql` |
| Cashier grant | `BusinessRoleBootstrapService`, `V54__business_roles_permissions.sql` |
| Receivable reads | `analytics/application/service/DashboardService.java` |
