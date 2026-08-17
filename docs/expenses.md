# Expenses (dépenses)

Source-backed guide for expense categories, expense CRUD, plan gating, retention,
and how amounts flow into dashboard / global-view analytics.

Verified against `ExpenseController`, `ExpenseService`, `Expense` / `ExpenseCategory`,
`ExpenseRepository`, `DashboardService`, `Plan.featureExpenses`,
`Plan.dataRetentionMonths`, and `V8` / `V32` / `V33` plan seeds.

## Intent

- Record operating costs against a business (optional store) and a category.
- Gate **create** of categories and expenses behind plan flag `featureExpenses`.
- Hide or 404 expenses older than the plan retention window on the expense APIs.
- Feed `expenseDate` sums into dashboard profit (`revenue - expenses`) and the
  global-view per-store breakdown (including unassigned « Communes »).

Amounts are integers (same convention as sale totals; typically XOF with no
fractional part). `ExpenseRequest.amount` must be `>= 1`.

## Public surface

Base path: `/api/v1/expenses`. JWT + business context required.

### Categories

| Method | Path | Permission |
|--------|------|------------|
| `POST` | `/expenses/categories` | `EXPENSES_CREATE` |
| `GET` | `/expenses/categories` | `EXPENSES_READ` |
| `PUT` | `/expenses/categories/{id}` | `EXPENSES_UPDATE` |
| `DELETE` | `/expenses/categories/{id}` | `EXPENSES_DELETE` |

Nav key `expenses` requires `EXPENSES_READ` (`NavigationPermissionRules`).

### Expenses

| Method | Path | Permission |
|--------|------|------------|
| `POST` | `/expenses` | `EXPENSES_CREATE` |
| `GET` | `/expenses` | `EXPENSES_READ` |
| `GET` | `/expenses/{id}` | `EXPENSES_READ` |
| `PUT` | `/expenses/{id}` | `EXPENSES_UPDATE` |
| `DELETE` | `/expenses/{id}` | `EXPENSES_DELETE` |

List query params: `categoryId`, `storeId`, `month`, `year`, `page` (default 0),
`size` (default 20, capped at 100).

## Plan / permission gates

| Action | Permission | `featureExpenses` |
|--------|------------|-------------------|
| Create category | `EXPENSES_CREATE` | Checked |
| Create expense | `EXPENSES_CREATE` | Checked |
| List / get / update / delete category | matching `EXPENSES_*` | **Not** checked |
| List / get / update / delete expense | matching `EXPENSES_*` | **Not** checked |
| Dashboard sums | `SALES_READ` or `PRODUCTS_READ` or `REPORTS_READ` | **Not** checked |
| Global-view sums | `GLOBAL_VIEW_READ` + `featureGlobalView` + `featureReports` | **Not** checked |

Gate implementation uses `getPlanForBusiness(...).ifPresent(...)`:

- **No plan** → create is **not** blocked by the feature flag.
- **Starter** (seeded) → `featureExpenses = false`.
- **Pro / Business** → `featureExpenses = true`.

Rejected create message: *« Suivi des dépenses non inclus dans votre plan. Passez à un plan supérieur. »*

## Categories

Request body (`ExpenseCategoryRequest`):

```json
{
  "name": "Loyer",
  "color": "#2563eb",
  "sortOrder": 10
}
```

- `name` is required (`@NotBlank`). DTO allows 255 characters; the column is
  `VARCHAR(100)` (`V1__initial_schema.sql`) — keep names ≤ 100.
- `color` optional, max 20 characters.
- `sortOrder` defaults to `0` when omitted.
- Create rejects if `existsByBusinessIdAndName` (exact, case-sensitive). There is
  **no** unique DB constraint, and **update does not re-check** uniqueness.
- Delete is blocked when any expense still uses the category:
  *« Impossible de supprimer cette catégorie : N dépense(s) l'utilisent. »*
- Categories are listed by `sortOrder` ascending. There is no retention filter
  on categories.

## Create / update expense

```bash
curl -X POST "http://localhost:8080/api/v1/expenses" \
  --header 'Authorization: Bearer <token>' \
  --header 'Content-Type: application/json' \
  --data '{
    "storeId": "<store-id-or-omit>",
    "categoryId": "<category-id>",
    "amount": 15000,
    "description": "Loyer août",
    "expenseDate": "2026-08-01",
    "receiptUrl": "https://example.com/receipts/loyer-aout.pdf"
  }'
```

Constraints:

| Field | Rule |
|-------|------|
| `categoryId` | Required. Create looks up `findByBusinessIdAndId`; missing → 404. Update **does not** re-validate in service (FK still applies). |
| `storeId` | Optional. Service does **not** verify the store belongs to the business; invalid UUID fails the `store_id` FK. `null` = unassigned / common expense. |
| `amount` | Required integer `>= 1`. |
| `expenseDate` | Required `LocalDate`. This date — not `createdAt` — drives list filters, retention, and dashboard sums. |
| `receiptUrl` | Optional string (`VARCHAR(500)`). There is **no** receipt-upload endpoint; the client stores a URL. |
| `userId` | Set from the authenticated user on **create** only; update does not change it. |

## List filters

`ExpenseService.list` applies filters in this order:

1. **Month + year** — both must be present and `month` in `1..12`. Builds
   `[first day, last day]` of that month. Incomplete or out-of-range month/year
   is ignored (falls through to retention-only or unfiltered list).
2. **Retention clamp** — if the plan has `dataRetentionMonths > 0`, list start
   is raised to `today.minusMonths(dataRetentionMonths)` (JVM default zone).
   If the clamped start is after the month end → empty page.
3. **Category vs store** — if both `categoryId` and `storeId` are set, **category
   wins**; the store filter is ignored.

## Retention window

Seeded `data_retention_months` (`V33__add_plan_retention_features.sql`):

| Plan | Months | Meaning |
|------|--------|---------|
| Starter | 3 | Hide expenses with `expenseDate` before `today - 3 months` |
| Pro | 12 | Same with 12 months |
| Business | 0 | Unlimited (`<= 0` disables the clamp) |
| No plan | — | No clamp |

On **get / update / delete**, an expense whose `expenseDate` is strictly before
the min date throws `ResourceNotFoundException` (same as a missing id — not a
distinct retention error).

Dashboard period start is clamped separately via
`SubscriptionService.clampPeriodStartToRetention` (same formula). Dashboard
does **not** 404 old rows; it just shortens the requested period.

## Dashboard and global view

`DashboardService` sums `Expense.amount` by `expenseDate` (inclusive range).
Profit is `revenue - expenses`.

| Context | Store scoping |
|---------|----------------|
| Dashboard with `storeId` | Only expenses whose `storeId` **equals** that store. Unassigned (`storeId = null`) are **excluded**. |
| Dashboard without `storeId` | All expenses for the business (assigned + unassigned). |
| Daily series `periodDailyExpenses` | Same store rule; grouped by `expenseDate`. |
| Global view totals | All expenses in the (retention-clamped) period. |
| Global view per-store rows | Assigned expenses on the store row. Unassigned totals appear as a synthetic row: `storeId = null`, name **« Communes »**, revenue `0`, profit `-amount`. |

Dashboard expense metrics do **not** require `featureExpenses`. A Starter tenant
that somehow has rows (seed, previous plan, or no-plan create) still sees them
in analytics, subject to retention / `featureReports` (no-reports plans are
forced to **today only**).

## Pitfalls

- Create is gated; read/update/delete of existing rows is not. Downgrading to
  Starter does not hide the module’s GET/PUT/DELETE if the user still has
  `EXPENSES_*`.
- `storeId` on create/update is not membership-checked in `ExpenseService`.
- Passing both `categoryId` and `storeId` on list drops the store filter.
- `month` without `year` (or invalid month) silently skips the date range.
- Category name uniqueness is create-only and not enforced in SQL.
- No receipt file storage — `receiptUrl` is opaque.
- Global-view store filter on the main dashboard excludes « Communes » costs;
  assign a `storeId` if those amounts should appear in a store-scoped P&L.

## Source codepaths

- `expense/infrastructure/web/ExpenseController`
- `expense/application/service/ExpenseService`
- `expense/domain/model/Expense`, `ExpenseCategory`
- `expense/domain/repository/ExpenseRepository` (list + dashboard sums)
- `analytics/application/service/DashboardService` (`todayExpenses`,
  `periodExpenses`, `periodDailyExpenses`, `getGlobalView` / `buildStoreStats`)
- `tenant/domain/model/Plan#featureExpenses`, `#dataRetentionMonths`
- `identity/domain/model/Permission` (`EXPENSES_*`)
- `identity/application/NavigationPermissionRules` (`expenses`)
