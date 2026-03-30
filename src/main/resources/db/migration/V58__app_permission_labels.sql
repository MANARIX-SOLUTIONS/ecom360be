-- Libellés et regroupement pour affichage (catalogue permissions SaaS)

ALTER TABLE app_permission
    ADD COLUMN IF NOT EXISTS label VARCHAR(350);
ALTER TABLE app_permission
    ADD COLUMN IF NOT EXISTS category VARCHAR(80);
ALTER TABLE app_permission
    ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

UPDATE app_permission AS ap
SET label       = u.label,
    category    = u.category,
    sort_order  = u.sort_order
FROM (VALUES
          ('PRODUCTS_CREATE', 'Produits — Créer', 'products', 10),
          ('PRODUCTS_READ', 'Produits — Consulter', 'products', 20),
          ('PRODUCTS_UPDATE', 'Produits — Modifier', 'products', 30),
          ('PRODUCTS_DELETE', 'Produits — Supprimer', 'products', 40),
          ('CATEGORIES_CREATE', 'Catégories — Créer', 'categories', 50),
          ('CATEGORIES_READ', 'Catégories — Consulter', 'categories', 60),
          ('CATEGORIES_UPDATE', 'Catégories — Modifier', 'categories', 70),
          ('CATEGORIES_DELETE', 'Catégories — Supprimer', 'categories', 80),
          ('STOCK_READ', 'Stock — Consulter', 'stock', 90),
          ('STOCK_INIT', 'Stock — Initialiser', 'stock', 100),
          ('STOCK_ADJUST', 'Stock — Ajuster', 'stock', 110),
          ('CLIENTS_CREATE', 'Clients — Créer', 'clients', 120),
          ('CLIENTS_READ', 'Clients — Consulter', 'clients', 130),
          ('CLIENTS_UPDATE', 'Clients — Modifier', 'clients', 140),
          ('CLIENTS_DELETE', 'Clients — Supprimer', 'clients', 150),
          ('SUPPLIERS_CREATE', 'Fournisseurs — Créer', 'suppliers', 160),
          ('SUPPLIERS_READ', 'Fournisseurs — Consulter', 'suppliers', 170),
          ('SUPPLIERS_UPDATE', 'Fournisseurs — Modifier', 'suppliers', 180),
          ('SUPPLIERS_DELETE', 'Fournisseurs — Supprimer', 'suppliers', 190),
          ('PURCHASE_ORDERS_CREATE', 'Commandes fournisseur — Créer', 'purchase_orders', 200),
          ('PURCHASE_ORDERS_READ', 'Commandes fournisseur — Consulter', 'purchase_orders', 210),
          ('PURCHASE_ORDERS_UPDATE', 'Commandes fournisseur — Modifier', 'purchase_orders', 220),
          ('PURCHASE_ORDERS_DELETE', 'Commandes fournisseur — Supprimer', 'purchase_orders', 230),
          ('SALES_CREATE', 'Ventes — Créer', 'sales', 240),
          ('SALES_READ', 'Ventes — Consulter', 'sales', 250),
          ('SALES_UPDATE', 'Ventes — Modifier', 'sales', 260),
          ('SALES_DELETE', 'Ventes — Supprimer', 'sales', 270),
          ('EXPENSES_CREATE', 'Dépenses — Créer', 'expenses', 280),
          ('EXPENSES_READ', 'Dépenses — Consulter', 'expenses', 290),
          ('EXPENSES_UPDATE', 'Dépenses — Modifier', 'expenses', 300),
          ('EXPENSES_DELETE', 'Dépenses — Supprimer', 'expenses', 310),
          ('DELIVERY_COURIERS_CREATE', 'Livreurs — Créer', 'delivery', 320),
          ('DELIVERY_COURIERS_READ', 'Livreurs — Consulter', 'delivery', 330),
          ('DELIVERY_COURIERS_UPDATE', 'Livreurs — Modifier', 'delivery', 340),
          ('DELIVERY_COURIERS_DELETE', 'Livreurs — Supprimer', 'delivery', 350),
          ('STORES_CREATE', 'Boutiques — Créer', 'stores', 360),
          ('STORES_READ', 'Boutiques — Consulter', 'stores', 370),
          ('STORES_UPDATE', 'Boutiques — Modifier', 'stores', 380),
          ('STORES_DELETE', 'Boutiques — Supprimer', 'stores', 390),
          ('GLOBAL_VIEW_READ', 'Vue globale — Consulter', 'global_view', 400),
          ('SUBSCRIPTION_READ', 'Abonnement — Consulter', 'subscription', 410),
          ('SUBSCRIPTION_UPDATE', 'Abonnement — Modifier', 'subscription', 420),
          ('BUSINESS_USERS_CREATE', 'Équipe — Inviter / créer', 'team', 430),
          ('BUSINESS_USERS_READ', 'Équipe — Consulter', 'team', 440),
          ('BUSINESS_USERS_UPDATE', 'Équipe — Modifier', 'team', 450),
          ('BUSINESS_USERS_DELETE', 'Équipe — Retirer', 'team', 460),
          ('API_KEYS_CREATE', 'Clés API — Créer', 'integrations', 470),
          ('API_KEYS_READ', 'Clés API — Consulter', 'integrations', 480),
          ('API_KEYS_DELETE', 'Clés API — Révoquer', 'integrations', 490),
          ('WEBHOOKS_CREATE', 'Webhooks — Créer', 'integrations', 500),
          ('WEBHOOKS_READ', 'Webhooks — Consulter', 'integrations', 510),
          ('WEBHOOKS_UPDATE', 'Webhooks — Modifier', 'integrations', 520),
          ('WEBHOOKS_DELETE', 'Webhooks — Supprimer', 'integrations', 530),
          ('COMMERCE_CONNECTIONS_CREATE', 'Connexions commerce — Créer', 'commerce', 540),
          ('COMMERCE_CONNECTIONS_READ', 'Connexions commerce — Consulter', 'commerce', 550),
          ('COMMERCE_CONNECTIONS_UPDATE', 'Connexions commerce — Modifier', 'commerce', 560),
          ('COMMERCE_CONNECTIONS_DELETE', 'Connexions commerce — Supprimer', 'commerce', 570)
     ) AS u(code, label, category, sort_order)
WHERE ap.code = u.code;

UPDATE app_permission
SET label      = code,
    category   = 'other',
    sort_order = 900
WHERE label IS NULL;

ALTER TABLE app_permission
    ALTER COLUMN label SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_app_permission_category_sort ON app_permission (category, sort_order);
