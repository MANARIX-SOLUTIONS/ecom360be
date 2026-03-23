-- Seed business (idempotent) — Demo Commerce SARL
-- Login: demo@ecom360.local / password123
INSERT INTO business (id, name, email, phone, address, currency, locale, status, created_at, updated_at)
VALUES ('a0000001-0000-4000-8000-000000000001', 'Demo Commerce SARL', 'demo@ecom360.local', '+225 07 00 00 00 01',
        'Plateau, Abidjan', 'XOF', 'fr', 'active', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
