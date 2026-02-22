-- Seed Minabio - business_user (idempotent)
INSERT INTO business_user (id, business_id, user_id, role, is_active, invited_at, accepted_at, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000002-0000-4000-8000-000000000002', u.id, 'proprietaire', true, NOW(), NOW(), NOW(), NOW()
FROM users u WHERE u.email = 'minabio@demo.local'
ON CONFLICT (business_id, user_id) DO NOTHING;

INSERT INTO business_user (id, business_id, user_id, role, is_active, invited_at, accepted_at, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000002-0000-4000-8000-000000000002', u.id, 'gestionnaire', true, NOW(), NOW(), NOW(), NOW()
FROM users u WHERE u.email = 'minabio_gestionnaire@demo.local'
ON CONFLICT (business_id, user_id) DO NOTHING;

INSERT INTO business_user (id, business_id, user_id, role, is_active, invited_at, accepted_at, created_at, updated_at)
SELECT gen_random_uuid(), 'a0000002-0000-4000-8000-000000000002', u.id, 'caissier', true, NOW(), NOW(), NOW(), NOW()
FROM users u WHERE u.email = 'minabio_caissier@demo.local'
ON CONFLICT (business_id, user_id) DO NOTHING;
