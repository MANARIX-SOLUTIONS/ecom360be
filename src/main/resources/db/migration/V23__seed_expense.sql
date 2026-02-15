-- Seed expense (idempotent)
INSERT INTO expense (id, business_id, store_id, user_id, category_id, amount, description, expense_date, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', u.id, '90000001-0000-4000-8000-000000000001', 50000, 'Loyer janvier', CURRENT_DATE - INTERVAL '20 days', NOW(), NOW()
FROM users u WHERE u.email = 'demo@ecom360.local' LIMIT 1;
