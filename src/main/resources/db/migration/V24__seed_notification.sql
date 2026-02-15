-- Seed notification (idempotent)
INSERT INTO notification (id, business_id, user_id, type, title, body, is_read, created_at)
SELECT gen_random_uuid(), 'a0000001-0000-4000-8000-000000000001', u.id, 'sale', 'Nouvelle vente', 'Vente RC-2024-00001 enregistrée', false, NOW() - INTERVAL '2 days'
FROM users u WHERE u.email = 'demo@ecom360.local' LIMIT 1;
