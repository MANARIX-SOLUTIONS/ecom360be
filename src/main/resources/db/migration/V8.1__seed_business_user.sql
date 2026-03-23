-- Seed business_user (idempotent)
INSERT INTO business_user (id, business_id, user_id, role, is_active, invited_at, accepted_at, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000001-0000-4000-8000-000000000001', u.id, 'proprietaire', true, NOW(), NOW(), NOW(), NOW()
FROM users u WHERE u.email = 'demo@ecom360.local'
ON CONFLICT (business_id, user_id) DO NOTHING;
