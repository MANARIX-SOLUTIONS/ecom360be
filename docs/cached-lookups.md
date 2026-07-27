# Cached lookups

`CachedLookups` centralizes hot, read-mostly tenant lookups behind Spring Cache
(Caffeine). Callers must authorize **before** invoking these methods so a cache
hit never bypasses permission checks.

## Intent

- Reduce repeated DB reads for categories, active plans, and per-user store lists.
- Keep cache keys and eviction in one component instead of scattering
  `@Cacheable` across services.
- Stay process-local: each API instance has its own Caffeine maps.

## Runtime configuration

`CacheConfig` registers named caches with:

| Setting | Value |
| --- | --- |
| Implementation | Caffeine via `CaffeineCacheManager` |
| Max entries | 1_000 |
| TTL | `expireAfterWrite` 10 minutes |
| Named caches | `plans`, `featureFlags`, `platformConfig`, `categories`, `stores` |

`application.yml` also sets `spring.cache.type=caffeine` with a default spec of
`maximumSize=500,expireAfterWrite=600s`. The `CacheManager` bean above is the
runtime source for these named caches. Actuator can expose cache metrics when
`caches` is included in `ACTUATOR_ENDPOINTS`.

## Cached methods

| Method | Cache / key | Contents |
| --- | --- | --- |
| `categoriesByBusiness(businessId)` | `categories` / `businessId` | Categories ordered by `sortOrder` |
| `activePlans()` | `plans` / `'active'` | Active plans ordered by monthly price |
| `storesForUser(businessId, userId)` | `stores` / `businessId:userId` | Active stores, filtered to assigned stores when the business user has any `business_user_store` rows |

Store-list semantics: if the user has **no** store assignments, all active
tenant stores are returned. If assignments exist, only those store IDs remain.

## Eviction

| Method | Effect | Typical callers |
| --- | --- | --- |
| `evictCategories(businessId)` | Drop that business's category list | `CategoryService` create/update/delete |
| `evictAllStores()` | Clear **all** `stores` entries | `StoreService`, `AdminStoreService`, `BusinessUserService` (assignment changes) |

There is currently **no** explicit eviction for `plans`. Plan catalogue changes
rely on the 10-minute TTL (or process restart). Do not assume admin plan edits
are visible immediately on every node.

`featureFlags` and `platformConfig` are registered for discovery but are not
populated by `CachedLookups` today.

## Call sites

Authorize in the service, then read through the cache:

- `CategoryService` — list via cache; mutate then `evictCategories`
- `StoreService` — list via `storesForUser`; mutate then `evictAllStores`
- `SubscriptionService.listPlans` — `activePlans()` after `SUBSCRIPTION_READ`
- `BusinessUserService` — `evictAllStores` when store assignments change
- `AdminStoreService` — `evictAllStores` on admin store mutations
- `DashboardService` / other services may still query repositories directly for
  analytics; do not assume every store/category read is cached

## Pitfalls

- Never put authz inside a `@Cacheable` method that is invoked after a successful
  permission check was skipped on a cache hit.
- Multi-instance deployments do not share Caffeine state. After a write on one
  node, other nodes can serve stale store/category data until TTL expiry unless
  you add a distributed cache or pub/sub eviction.
- `evictAllStores()` is coarse: any store or assignment change invalidates every
  user's store list cache entry.
- Changing plan feature flags in the database can leave stale `plans` cache
  entries for up to 10 minutes.

## Implementation map

- Lookups: `shared/infrastructure/cache/CachedLookups`
- Manager: `shared/infrastructure/config/CacheConfig`
- Consumers: `catalog/.../CategoryService`, `store/.../StoreService`,
  `tenant/.../SubscriptionService`, `tenant/.../BusinessUserService`,
  `admin/.../AdminStoreService`
