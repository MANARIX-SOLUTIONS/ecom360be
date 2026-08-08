# Dashboard analytics

The dashboard API provides tenant KPIs, store-level filtering, comparison periods, stock alerts,
and cross-store reporting. This guide documents the behavior that API consumers cannot infer from
the response schema alone.

## Request contract

All dashboard endpoints require:

- a bearer token whose JWT claims contain the business context;
- an active or trialing subscription (otherwise the subscription filter returns HTTP `402`);
- the permissions listed below.

The API does not select a business from `X-Business-Id`; the token's `businessId` is authoritative.
Dates use ISO `yyyy-MM-dd`. Ranges are inclusive. Sale timestamps are bounded using the application
server's time zone, while expenses are compared directly by `expenseDate`.

| Endpoint | Permission | Purpose |
| --- | --- | --- |
| `GET /api/v1/dashboard` | Any of `SALES_READ`, `PRODUCTS_READ`, `REPORTS_READ` | KPIs and preview lists, optionally for one store |
| `GET /api/v1/dashboard/top-products` | Any of `SALES_READ`, `PRODUCTS_READ`, `REPORTS_READ` | Paginated product ranking |
| `GET /api/v1/dashboard/low-stock-items` | Any of `SALES_READ`, `PRODUCTS_READ`, `REPORTS_READ` | Paginated low-stock rows |
| `GET /api/v1/dashboard/global` | `GLOBAL_VIEW_READ` | Cross-store KPIs and distribution |

For example:

```bash
curl --get 'http://localhost:8080/api/v1/dashboard' \
  --header 'Authorization: Bearer <access-token>' \
  --data-urlencode 'periodStart=2026-05-01' \
  --data-urlencode 'periodEnd=2026-05-31' \
  --data-urlencode 'storeId=<store-id>'
```

If dates are omitted, the controller requests the first day of the current month through today.
The API can change that requested range according to the subscription rules below.

## Effective period

The main dashboard and top-products endpoint resolve the reporting period in this order:

1. A plan without `featureReports` forces both dates to today.
2. `periodStart` is moved forward to the plan's earliest retained date, if necessary.
3. If `periodEnd` is before the resulting start, the end is set equal to the start.

Use `periodStart` and `periodEnd` returned by the main dashboard or global-view response as the
authoritative effective range. The paginated top-products response does not echo its effective
dates. The service does not otherwise reject reversed ranges or cap future end dates.

The seeded plans currently behave as follows:

| Plan | Dashboard period | Low-stock alerts | Gross-margin fields | Global view |
| --- | --- | --- | --- | --- |
| Starter | Today only; `analyticsLimitedToToday=true` | Hidden | `null` and `[]` | Denied |
| Pro | Requested range, limited to 12 months of history | Available | `null` and `[]` | Available |
| Business | Requested range, unlimited history | Available | Available | Available |

Plan feature flags are the runtime source of truth; deployments may change plan records after the
seed migrations.

## Main dashboard semantics

`GET /api/v1/dashboard` returns several groups of values:

### Sales and expenses

- `periodRevenue` and `periodSalesCount` include only sales whose status is exactly `completed`.
- `periodExpenses` uses each expense's `expenseDate`, not its creation timestamp.
- `periodProfit` is `periodRevenue - periodExpenses`. It is not gross margin.
- `todaySalesCount` counts sales created today regardless of status.
- `todayExpenses` always covers today.
- `todayRevenue` is calculated from completed sales already loaded for the effective period.
  Consequently, a historical period that excludes today returns `todayRevenue=0`, even though
  `todaySalesCount` and `todayExpenses` still report today's values.

All monetary values are unformatted integer domain amounts.

### Previous-period comparison

The `previousPeriod*` fields use a range of the same inclusive length immediately before the
effective current period. For example, an effective May 10-12 range is compared with May 7-9.
Revenue and sale count again include only completed sales; profit is revenue minus expenses.

The comparison range is derived after the current start is retention-clamped and is not clamped a
second time. Do not use these comparison fields as proof that a date falls inside the plan's
retention window.

### Lists and totals

- `topProducts` ranks completed-sale lines by revenue and contains at most 10 entries.
  `topProductsTotal` reports the full ranking size.
- `lowStockItems` contains at most 10 rows and is sorted by store name, then product name.
  A row is low stock when `quantity <= minStock`; `lowStockItemsTotal` reports the full size.
- `recentSales` contains at most 10 of the latest sales and is not filtered to the requested
  period. Plans without reports filter this list to today.
- `periodGrossMargin` and `topMarginProducts` are populated only when
  `featureAdvancedReports=true`. Margin uses each product's current `costPrice`, not a historical
  cost snapshot, so changing catalog costs can change historical margin results.
- `debtorClientsCount` counts active clients with a positive credit balance.
  `totalReceivable` sums those positive balances.
- `businessCreatedAt` is an ISO-8601 instant used by clients for onboarding behavior.

### Daily series and payment mix

The main dashboard also returns sparse day-level series for charting:

- `periodDailySales` — completed-sale revenue grouped by civil day over the effective period.
  Day boundaries use `DATE(createdAt)` in the database session timezone. The Instant window is
  built from `ZoneId.systemDefault()`, so keep the JVM and database clocks/zones aligned.
- `periodDailyExpenses` — expense amounts grouped by `expenseDate` (inclusive local dates).
- `periodPaymentBreakdown` — completed-sale revenue grouped by `paymentMethod`.

Days or payment methods with zero activity are omitted. Amounts are integer domain totals, not
formatted currency strings. Both series and the payment mix respect the optional `storeId` filter.

Example shape:

```json
{
  "periodDailySales": [{"date": "2026-07-01", "amount": 125000}],
  "periodDailyExpenses": [{"date": "2026-07-01", "amount": 18000}],
  "periodPaymentBreakdown": [{"method": "cash", "amount": 90000}]
}
```

### Store filtering

When `storeId` is supplied, sales, expenses, profit, product rankings, margin rankings, recent
sales, daily series, payment breakdown, and low-stock rows are scoped to that store. The following
values remain tenant-wide:

- `totalProducts`, `totalClients`, `totalSuppliers`, and `totalStores`;
- `debtorClientsCount` and `totalReceivable`;
- `businessCreatedAt`.

`totalClients` and `totalSuppliers` count active records. `totalProducts` counts all tenant
products.

Analytics does not apply a user's assigned-store list. A user with any qualifying dashboard
permission can query any store belonging to the JWT's tenant, and an unfiltered request covers all
tenant stores.

## Paginated lists

The full preview lists are available through:

```text
GET /api/v1/dashboard/top-products?periodStart=2026-05-01&periodEnd=2026-05-31&page=0&size=25
GET /api/v1/dashboard/low-stock-items?page=0&size=25
```

Both return:

```json
{
  "content": [],
  "total": 0,
  "page": 0,
  "size": 25,
  "hasNext": false
}
```

Pagination is zero-based. Negative pages become `0`; sizes are clamped to the range `1..50`.
The top-products period follows the same effective-period rules as the main dashboard. Low-stock
pagination has no date range and returns an empty slice when the plan disables stock alerts.

## Global view

`GET /api/v1/dashboard/global` aggregates tenant stores and does not accept `storeId`. It requires:

1. `GLOBAL_VIEW_READ`;
2. `featureGlobalView=true`;
3. `featureReports=true`.

It returns completed-sale revenue and count, average basket, expenses, profit, per-store stats,
low-stock preview rows, and the top 10 products. The requested start is subject to plan retention,
and the effective dates are returned in the response.

Each `salesByStore` entry includes `revenue`, `salesCount`, `sharePercent` (of tenant revenue),
`expenses`, `profit` (`revenue - expenses`), and `expenseSharePercent` (of tenant expenses).
Shares are rounded to one decimal place. The current comparator sorts by revenue ascending,
then by expenses descending for equal revenue (see `DashboardService.buildStoreStats`).

`storeCount` includes inactive stores. `salesByStore` includes stores that have completed sales
**or** store-scoped expenses in the period (expense-only stores appear with zero revenue).
Expenses with a null `storeId` are returned as a synthetic row named `Communes` with
`storeId=null`, zero revenue/sales, and negative profit equal to those shared expenses.
Low-stock rows also scan inactive stores and are returned as one unpaginated preview list
(capped at 10).

```bash
curl --get 'http://localhost:8080/api/v1/dashboard/global' \
  --header 'Authorization: Bearer <access-token>' \
  --data-urlencode 'periodStart=2026-01-01' \
  --data-urlencode 'periodEnd=2026-05-31'
```

## Troubleshooting

- **Requested and returned dates differ:** check `analyticsLimitedToToday` and the current plan's
  report and retention features.
- **Today count and revenue appear inconsistent:** the count includes every sale status, while
  revenue includes only completed sales and only when today is in the effective period.
- **Profit and gross margin differ:** profit subtracts expenses; gross margin subtracts current
  product costs and is an advanced-report field.
- **Low-stock arrays are empty:** verify `featureStockAlerts` before assuming no product is low.
- **Only 10 products or stock rows are present:** use the paginated endpoints and their `total`
  fields.
- **Global view returns access denied:** both the role permission and the two plan features are
  enforced independently.
- **Daily sales days look shifted:** verify JVM and PostgreSQL time zones; sale day grouping uses
  `DATE(createdAt)` while expenses use `expenseDate`.
- **Global `Communes` row appears:** some expenses in the period have no `storeId`; they are
  intentionally not allocated to a boutique.

## Implementation map

- HTTP contract: `analytics/infrastructure/web/DashboardController`
- Aggregation and plan behavior: `analytics/application/service/DashboardService`
- Response schemas: `analytics/application/dto`
- Daily/payment queries: `sales/domain/repository/SaleRepository`,
  `expense/domain/repository/ExpenseRepository`
- Retention and plan gates: `tenant/application/service/SubscriptionService`
- Default plan features: migrations `V32`, `V33`, and `V53`
- Dashboard permissions: migrations `V54` and `V59`

Swagger UI remains the source for the complete generated response schema:
`http://localhost:8080/swagger-ui.html`.
