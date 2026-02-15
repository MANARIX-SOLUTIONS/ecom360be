-- Seed supplier (idempotent)
INSERT INTO supplier (id, business_id, name, phone, email, address, balance, is_active, created_at, updated_at)
VALUES ('70000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', 'Fournisseur Coca-Cola', '+225 27 00 00 00 01', 'contact@coca.ci', 'Zone 4', 0, true, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
