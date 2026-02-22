-- Seed Minabio - Subscription (idempotent)
INSERT INTO subscription (id, business_id, plan_id, billing_cycle, status, current_period_start, current_period_end, created_at, updated_at)
SELECT '80000002-0000-4000-8000-000000000002', 'a0000002-0000-4000-8000-000000000002', p.id, 'monthly', 'active',
       CURRENT_DATE - INTERVAL '15 days', CURRENT_DATE + INTERVAL '15 days', NOW(), NOW()
FROM plan p WHERE p.slug = 'pro' LIMIT 1
ON CONFLICT (id) DO NOTHING;
