-- Seed webhook (idempotent)
INSERT INTO webhook (id, business_id, url, events, secret_hash, is_active, created_at, updated_at)
VALUES (gen_random_uuid(), 'a0000001-0000-4000-8000-000000000001', 'https://example.com/webhook/demo', 'sale.created,sale.updated',
        'demo_secret_hash', false, NOW(), NOW());
