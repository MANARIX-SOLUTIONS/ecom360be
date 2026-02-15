-- Seed api_key (idempotent)
INSERT INTO api_key (id, business_id, key_hash, label, permissions, is_active, created_at)
VALUES (gen_random_uuid(), 'a0000001-0000-4000-8000-000000000001', 'demo_api_key_hash_placeholder', 'Clé démo', 'read:products,read:sales', true, NOW())
ON CONFLICT (key_hash) DO NOTHING;
