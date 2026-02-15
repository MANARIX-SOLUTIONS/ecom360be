-- Seed category (idempotent)
INSERT INTO category (id, business_id, name, color, sort_order, created_at)
VALUES
    ('d0000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', 'Boissons', '#3B82F6', 0, NOW()),
    ('d0000002-0000-4000-8000-000000000002', 'a0000001-0000-4000-8000-000000000001', 'Snacks', '#10B981', 1, NOW())
ON CONFLICT (id) DO NOTHING;
