-- Seed invoice (idempotent)
INSERT INTO invoice (id, business_id, subscription_id, number, amount, status, payment_method, due_date, created_at)
SELECT gen_random_uuid(), 'a0000001-0000-4000-8000-000000000001', '80000001-0000-4000-8000-000000000001', 'INV-DEMO-001', 5000, 'paid', 'card',
       CURRENT_DATE - INTERVAL '10 days', NOW()
FROM (SELECT 1) x
WHERE EXISTS (SELECT 1 FROM business WHERE id = 'a0000001-0000-4000-8000-000000000001')
  AND NOT EXISTS (SELECT 1 FROM invoice WHERE number = 'INV-DEMO-001');
