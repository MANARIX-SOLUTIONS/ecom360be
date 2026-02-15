-- Seed expense_category (idempotent)
INSERT INTO expense_category (id, business_id, name, color, sort_order, created_at)
VALUES
    ('90000001-0000-4000-8000-000000000001', 'a0000001-0000-4000-8000-000000000001', 'Loyer', '#EF4444', 0, NOW()),
    ('90000002-0000-4000-8000-000000000002', 'a0000001-0000-4000-8000-000000000001', 'Électricité', '#F59E0B', 1, NOW())
ON CONFLICT (id) DO NOTHING;
