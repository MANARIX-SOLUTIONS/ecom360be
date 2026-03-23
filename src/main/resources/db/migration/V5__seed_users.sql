-- Seed users (idempotent) — BCrypt hash for "password123"
INSERT INTO users (id, full_name, email, phone, password_hash, locale, is_platform_admin, is_active, created_at, updated_at)
VALUES ('b0000001-0000-4000-8000-000000000001', 'Demo Utilisateur', 'demo@ecom360.local', '+225 07 00 00 00 01',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'fr', false, true, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
