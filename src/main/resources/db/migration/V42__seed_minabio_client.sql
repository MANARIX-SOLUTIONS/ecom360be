-- Seed Minabio - Clients (idempotent)
-- Clients fictifs pour démo - style Dakar/Sénégal
INSERT INTO client (id, business_id, name, phone, email, address, credit_balance, is_active, created_at, updated_at)
VALUES
    ('f0000010-0000-4000-8000-000000000010', 'a0000002-0000-4000-8000-000000000002', 'Awa Diallo', '+221 77 123 45 67', 'awa.diallo@example.com', 'Plateau, Dakar', 0, true, NOW(), NOW()),
    ('f0000011-0000-4000-8000-000000000011', 'a0000002-0000-4000-8000-000000000002', 'Mamadou Sow', '+221 76 234 56 78', NULL, 'Almadies, Dakar', 0, true, NOW(), NOW()),
    ('f0000012-0000-4000-8000-000000000012', 'a0000002-0000-4000-8000-000000000002', 'Fatou Ndiaye', '+221 70 345 67 89', 'fatou.ndiaye@example.com', 'Keur Massar, Dakar', 0, true, NOW(), NOW()),
    ('f0000013-0000-4000-8000-000000000013', 'a0000002-0000-4000-8000-000000000002', 'Ibrahima Fall', '+221 77 456 78 90', NULL, 'Pikine, Dakar', 0, true, NOW(), NOW()),
    ('f0000014-0000-4000-8000-000000000014', 'a0000002-0000-4000-8000-000000000002', 'Aminata Ba', '+221 76 567 89 01', 'aminata.ba@example.com', 'Mermoz, Dakar', 0, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
