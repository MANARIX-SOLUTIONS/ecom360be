-- Seed purchase_order_line (idempotent)
INSERT INTO purchase_order_line (id, purchase_order_id, product_id, quantity, unit_cost, line_total)
VALUES
    (gen_random_uuid(), '50000001-0000-4000-8000-000000000001', 'e0000001-0000-4000-8000-000000000001', 50, 150, 7500),
    (gen_random_uuid(), '50000001-0000-4000-8000-000000000001', 'e0000002-0000-4000-8000-000000000002', 50, 140, 7000);
