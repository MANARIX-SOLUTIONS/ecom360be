-- Seed client_payment (idempotent)
INSERT INTO client_payment (id, client_id, store_id, user_id, amount, payment_method, note, created_at)
SELECT gen_random_uuid(), 'f0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', u.id, 5000, 'cash', 'Acompte vente', NOW() - INTERVAL '3 days'
FROM users u WHERE u.email = 'demo@ecom360.local' LIMIT 1;
