-- Seed sale (idempotent)
INSERT INTO sale (id, business_id, store_id, user_id, client_id, receipt_number, payment_method, subtotal, discount_amount, total, amount_received, change_given, status, created_at)
SELECT '60000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', u.id, 'f0000001-0000-4000-8000-000000000001', 'RC-2024-00001', 'cash', 850, 0, 850, 1000, 150, 'completed', NOW() - INTERVAL '2 days'
FROM users u WHERE u.email = 'demo@ecom360.local'
ON CONFLICT (id) DO NOTHING;
