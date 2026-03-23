-- Seed platform_config (idempotent)
INSERT INTO platform_config (id, key, value, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'app_name', '360 PME Commerce', NOW(), NOW()),
    (gen_random_uuid(), 'default_currency', 'XOF', NOW(), NOW()),
    (gen_random_uuid(), 'maintenance_mode', 'false', NOW(), NOW())
ON CONFLICT (key) DO NOTHING;
