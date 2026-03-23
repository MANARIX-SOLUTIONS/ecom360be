-- Seed sale_line (idempotent)
INSERT INTO sale_line (id, sale_id, product_id, product_name, quantity, unit_price, line_total)
VALUES
    (gen_random_uuid(), '60000001-0000-4000-8000-000000000001', 'e0000001-0000-4000-8000-000000000001', 'Coca-Cola 33cl', 2, 250, 500),
    (gen_random_uuid(), '60000001-0000-4000-8000-000000000001', 'e0000003-0000-4000-8000-000000000003', 'Chips Lay''s 50g', 1, 350, 350);
