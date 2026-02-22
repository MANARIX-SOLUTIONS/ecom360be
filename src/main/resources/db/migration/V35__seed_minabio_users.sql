-- Seed Minabio - Users (idempotent) — BCrypt hash for "password123"
INSERT INTO users (id, full_name, email, phone, password_hash, locale, is_platform_admin, is_active, created_at, updated_at)
VALUES
    ('b0000002-0000-4000-8000-000000000002', 'Minabio Admin', 'minabio@demo.local', '+221 77 763 84 28',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'fr', false, true, NOW(), NOW()),
    ('b0000003-0000-4000-8000-000000000003', 'Minabio Gestionnaire', 'minabio_gestionnaire@demo.local', '+221 76 111 22 33',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'fr', false, true, NOW(), NOW()),
    ('b0000004-0000-4000-8000-000000000004', 'Minabio Caissier', 'minabio_caissier@demo.local', '+221 76 444 55 66',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'fr', false, true, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
