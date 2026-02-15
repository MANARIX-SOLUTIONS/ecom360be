-- Seed store (idempotent)
INSERT INTO store (id, business_id, name, address, phone, is_active, created_at, updated_at)
VALUES ('c0000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', 'Magasin Principal', 'Plateau, Abidjan', '+225 07 00 00 00 01', true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
