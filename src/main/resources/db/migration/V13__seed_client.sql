-- Seed client (idempotent)
INSERT INTO client (id, business_id, name, phone, email, address, credit_balance, is_active, created_at, updated_at)
VALUES
    ('f0000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', 'Client Démo', '+225 07 11 11 11 11', 'client@example.com', 'Cocody', 0, true, NOW(), NOW()),
    ('f0000002-0000-4000-8000-000000000002', 'a0000001-0000-4000-8000-000000000001', 'Marie Kouassi', '+225 07 22 22 22 22', NULL, 'Yopougon', 0, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
