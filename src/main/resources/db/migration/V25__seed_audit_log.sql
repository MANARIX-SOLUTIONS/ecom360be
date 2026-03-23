-- Seed audit_log (idempotent)
INSERT INTO audit_log (id, business_id, user_id, action, entity_type, entity_id, created_at)
SELECT gen_random_uuid(), 'a0000001-0000-4000-8000-000000000001', u.id, 'create', 'sale', '60000001-0000-4000-8000-000000000001', NOW() - INTERVAL '2 days'
FROM users u WHERE u.email = 'demo@ecom360.local' LIMIT 1;
