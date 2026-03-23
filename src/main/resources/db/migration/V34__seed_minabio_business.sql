-- Seed Minabio - Business (idempotent)
-- Données extraites de https://mina-bio.com/ - Produits agroalimentaires locaux, Dakar Sénégal
-- Login: minabio@demo.local / password123
INSERT INTO business (id, name, email, phone, address, tax_id, currency, locale, status, created_at, updated_at)
VALUES ('a0000002-0000-4000-8000-000000000002', 'MINABIO', 'minabio@demo.local', '+221 77 763 84 28',
        'Keur Massar, Dakar, Sénégal', '009146623', 'XOF', 'fr', 'active', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
