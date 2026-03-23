-- Seed product_store_stock (idempotent)
INSERT INTO product_store_stock (id, product_id, store_id, quantity, min_stock, updated_at)
VALUES
    (gen_random_uuid(), 'e0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 50, 10, NOW()),
    (gen_random_uuid(), 'e0000002-0000-4000-8000-000000000002', 'c0000001-0000-4000-8000-000000000001', 45, 10, NOW()),
    (gen_random_uuid(), 'e0000003-0000-4000-8000-000000000003', 'c0000001-0000-4000-8000-000000000001', 30, 5, NOW())
ON CONFLICT (product_id, store_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = NOW();
