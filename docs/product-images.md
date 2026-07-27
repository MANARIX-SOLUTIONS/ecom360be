# Product images

Products can store an `imageUrl` and optionally host the binary on the API host.
Uploads are tenant-scoped files served from a public URL so POS and catalog UIs
can render `<img>` tags without a bearer token.

## Intent

- Keep catalog/stock responses self-contained with an `imageUrl` field.
- Prefer managed uploads for local/VPS deployments; allow an external URL when
  callers set `imageUrl` directly on create/update.
- Isolate public file serving under `/api/v1/public/**` (no JWT, no subscription
  gate).

## Upload contract

```http
POST /api/v1/products/{id}/image/upload
Authorization: Bearer <access-token>
Content-Type: multipart/form-data
```

| Constraint | Value |
| --- | --- |
| Permission | `PRODUCTS_UPDATE` (or `ROLE_PLATFORM_ADMIN`) |
| Form field | `file` (`MultipartFile`) |
| Max size | 8 MB (aligned with `spring.servlet.multipart.max-file-size`) |
| Allowed MIME | `image/png`, `image/jpeg`, `image/webp`, `image/gif` |
| Stored path shape | `/api/v1/public/product-images/{businessId}/{uuid}.{ext}` |

Example:

```bash
curl -X POST "http://localhost:8080/api/v1/products/<product-id>/image/upload" \
  --header 'Authorization: Bearer <access-token>' \
  --form 'file=@./photo.png;type=image/png'
```

On success the product is returned with the new relative `imageUrl`. Any previous
**managed** image for that product is deleted from disk first.

## Public download

```http
GET /api/v1/public/product-images/{businessId}/{filename}
```

- No authentication.
- Filenames must match
  `{uuid}.{png|jpg|jpeg|webp|gif}` or the handler returns `404`.
- Path traversal is rejected (`normalize` + prefix check).
- Successful responses set `Cache-Control: public, max-age=86400`.

Stock list responses (`StockLevelResponse.imageUrl`) expose the same product URL
for POS grids.

## Create / update / delete behavior

| Action | Behavior |
| --- | --- |
| `POST/PUT /products` with `imageUrl` | Persists the string as-is (external or managed). |
| `PUT` clearing `imageUrl` to null/blank | Deletes the previous managed file when its URL matches this API's public prefix. |
| `POST .../image/upload` | Replaces managed file and overwrites `imageUrl`. |
| Soft-delete product | Deletes managed file if present, then sets `imageUrl=null`. |

Only URLs under `/api/v1/public/product-images/{businessId}/` are treated as
managed. External CDN/S3 URLs are left untouched on disk.

## Storage layout

Configured via `app.files.product-images-dir` /
`PRODUCT_IMAGES_DIR` (default `./data/uploads/product-images`).

```text
{PRODUCT_IMAGES_DIR}/
  {businessId}/
    {uuid}.png
```

The Docker image defaults to `/app/data/uploads/product-images` and creates the
directory at build time. Mount a volume on that path (or set
`PRODUCT_IMAGES_DIR`) so uploads survive container recreation. The storage bean
fails fast at startup if the directory cannot be created.

Business logos use the parallel path `BUSINESS_LOGOS_DIR` /
`/api/v1/public/business-logos/...`.

## Pitfalls

- Upload MIME must be one of the allowed types; browsers that send
  `application/octet-stream` will be rejected.
- Multipart max sizes are also enforced by Spring (`max-file-size` 8 MB,
  `max-request-size` 10 MB).
- Public images are intentionally unauthenticated. Treat product photos as
  non-sensitive assets; do not store private documents on this path.
- Relative `imageUrl` values assume the frontend prefixes the API origin.

## Implementation map

- Upload HTTP: `catalog/infrastructure/web/ProductController`
- Public serve: `catalog/infrastructure/web/PublicProductImageController`
- Disk I/O: `catalog/infrastructure/storage/ProductImageStorageService`
- Lifecycle: `catalog/application/service/ProductService`
- Stock echo: `inventory/application/service/StockService`
- Config: `shared/infrastructure/config/AppFilesProperties`, `application.yml`
- Security allow-list: `identity/infrastructure/security/SecurityConfig` (`/api/v1/public/**`)
