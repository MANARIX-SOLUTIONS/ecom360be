-- Seed stock_movement (idempotent)
INSERT INTO stock_movement (id, product_id, store_id, user_id, type, quantity, quantity_before, quantity_after, reference, note, created_at)
SELECT gen_random_uuid(), 'e0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', u.id, 'in', 50, 0, 50, 'INIT', 'Stock initial', NOW() - INTERVAL '5 days'
FROM users u WHERE u.email = 'demo@ecom360.local' LIMIT 1;
