-- Seed Minabio - business_user_store (assignations employé → boutique)
-- Gestionnaire : accès aux 2 boutiques
INSERT INTO business_user_store (business_user_id, store_id, created_at, updated_at)
SELECT bu.id, 'c0000002-0000-4000-8000-000000000002', NOW(), NOW()
FROM business_user bu
JOIN users u ON bu.user_id = u.id
WHERE bu.business_id = 'a0000002-0000-4000-8000-000000000002'
  AND u.email = 'minabio_gestionnaire@demo.local'
ON CONFLICT (business_user_id, store_id) DO NOTHING;

INSERT INTO business_user_store (business_user_id, store_id, created_at, updated_at)
SELECT bu.id, 'c0000001-0000-4000-8000-000000000001', NOW(), NOW()
FROM business_user bu
JOIN users u ON bu.user_id = u.id
WHERE bu.business_id = 'a0000002-0000-4000-8000-000000000002'
  AND u.email = 'minabio_gestionnaire@demo.local'
ON CONFLICT (business_user_id, store_id) DO NOTHING;

-- Caissier : accès à Boutique Keur Massar uniquement
INSERT INTO business_user_store (business_user_id, store_id, created_at, updated_at)
SELECT bu.id, 'c0000002-0000-4000-8000-000000000002', NOW(), NOW()
FROM business_user bu
JOIN users u ON bu.user_id = u.id
WHERE bu.business_id = 'a0000002-0000-4000-8000-000000000002'
  AND u.email = 'minabio_caissier@demo.local'
ON CONFLICT (business_user_id, store_id) DO NOTHING;
