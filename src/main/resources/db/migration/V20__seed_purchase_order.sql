-- Seed purchase_order (idempotent)
INSERT INTO purchase_order (id, business_id, supplier_id, store_id, user_id, reference, status, total_amount, expected_date, created_at, updated_at)
SELECT '50000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', '70000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', u.id, 'PO-2024-001', 'received', 15000, CURRENT_DATE - INTERVAL '5 days', NOW() - INTERVAL '6 days', NOW()
FROM users u WHERE u.email = 'demo@ecom360.local'
ON CONFLICT (id) DO NOTHING;
