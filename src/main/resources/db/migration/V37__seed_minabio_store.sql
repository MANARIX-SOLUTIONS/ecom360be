-- Seed Minabio - Store (idempotent)
INSERT INTO store (id, business_id, name, address, phone, is_active, created_at, updated_at)
VALUES
    ('c0000002-0000-4000-8000-000000000002', 'a0000002-0000-4000-8000-000000000002', 'Boutique Keur Massar', 'Keur Massar, Dakar, Sénégal', '+221 76 736 36 21', true, NOW(), NOW()),
    ('c0000003-0000-4000-8000-000000000003', 'a0000002-0000-4000-8000-000000000002', 'Boutique Plateau', 'Plateau, Dakar, Sénégal', '+221 77 519 37 92', true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
