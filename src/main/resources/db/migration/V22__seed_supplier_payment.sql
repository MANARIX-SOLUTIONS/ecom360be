-- Seed supplier_payment (idempotent)
INSERT INTO supplier_payment (id, supplier_id, user_id, amount, payment_method, note, created_at)
SELECT gen_random_uuid(), '70000001-0000-4000-8000-000000000001', u.id, 15000, 'bank_transfer', 'Règlement PO-2024-001', NOW() - INTERVAL '4 days'
FROM users u WHERE u.email = 'demo@ecom360.local' LIMIT 1;
