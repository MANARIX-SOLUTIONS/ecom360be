-- Seed Minabio - Categories (idempotent)
-- Catégories extraites de https://mina-bio.com/
INSERT INTO category (id, business_id, name, color, sort_order, created_at)
VALUES
    ('d0000010-0000-4000-8000-000000000010', 'a0000002-0000-4000-8000-000000000002', 'Produits Gourmands', '#F59E0B', 0, NOW()),
    ('d0000011-0000-4000-8000-000000000011', 'a0000002-0000-4000-8000-000000000002', 'Céréales', '#84CC16', 1, NOW()),
    ('d0000012-0000-4000-8000-000000000012', 'a0000002-0000-4000-8000-000000000002', 'Poudres & Épices', '#8B5CF6', 2, NOW()),
    ('d0000013-0000-4000-8000-000000000013', 'a0000002-0000-4000-8000-000000000002', 'Huiles', '#F97316', 3, NOW()),
    ('d0000014-0000-4000-8000-000000000014', 'a0000002-0000-4000-8000-000000000002', 'Tisanes & Boissons', '#06B6D4', 4, NOW())
ON CONFLICT (id) DO NOTHING;
