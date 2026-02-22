-- Seed Minabio - Sales (idempotent)
-- Ventes de démo pour tester le tableau de bord
INSERT INTO sale (id, business_id, store_id, user_id, client_id, receipt_number, payment_method, subtotal, discount_amount, total, amount_received, change_given, status, created_at)
SELECT '60000010-0000-4000-8000-000000000010', 'a0000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', u.id, 'f0000010-0000-4000-8000-000000000010', 'MB-2024-00001', 'wave', 13000, 0, 13000, 13000, 0, 'completed', NOW() - INTERVAL '3 days'
FROM users u WHERE u.email = 'minabio@demo.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO sale (id, business_id, store_id, user_id, client_id, receipt_number, payment_method, subtotal, discount_amount, total, amount_received, change_given, status, created_at)
SELECT '60000011-0000-4000-8000-000000000011', 'a0000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', u.id, 'f0000011-0000-4000-8000-000000000011', 'MB-2024-00002', 'orange_money', 13500, 500, 13000, 13000, 0, 'completed', NOW() - INTERVAL '2 days'
FROM users u WHERE u.email = 'minabio@demo.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO sale (id, business_id, store_id, user_id, client_id, receipt_number, payment_method, subtotal, discount_amount, total, amount_received, change_given, status, created_at)
SELECT '60000012-0000-4000-8000-000000000012', 'a0000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', u.id, NULL, 'MB-2024-00003', 'cash', 5000, 0, 5000, 5000, 0, 'completed', NOW() - INTERVAL '1 day'
FROM users u WHERE u.email = 'minabio@demo.local'
ON CONFLICT (id) DO NOTHING;
