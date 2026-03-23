-- Seed subscription (idempotent)
INSERT INTO subscription (id, business_id, plan_id, billing_cycle, status, current_period_start, current_period_end, created_at, updated_at)
SELECT '80000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', p.id, 'monthly', 'active',
       CURRENT_DATE - INTERVAL '15 days', CURRENT_DATE + INTERVAL '15 days', NOW(), NOW()
FROM plan p WHERE p.slug = 'starter' LIMIT 1
ON CONFLICT (id) DO NOTHING;
