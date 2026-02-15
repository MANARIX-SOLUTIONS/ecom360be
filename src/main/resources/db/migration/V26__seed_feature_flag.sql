-- Seed feature_flag (idempotent)
INSERT INTO feature_flag (id, key, label, description, is_enabled, updated_at)
VALUES
    (gen_random_uuid(), 'pos_enabled', 'POS activé', 'Point de vente disponible', true, NOW()),
    (gen_random_uuid(), 'expenses_enabled', 'Dépenses', 'Module dépenses activé', true, NOW()),
    (gen_random_uuid(), 'reports_enabled', 'Rapports', 'Rapports avancés', true, NOW())
ON CONFLICT (key) DO NOTHING;
