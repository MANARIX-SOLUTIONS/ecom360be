-- Rôles par entreprise + permissions applicatives (multi-tenant)

CREATE TABLE app_permission (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(100) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE business_role (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  is_system BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (business_id, code)
);

CREATE TABLE business_role_permission (
  role_id UUID NOT NULL REFERENCES business_role(id) ON DELETE CASCADE,
  permission_id UUID NOT NULL REFERENCES app_permission(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

ALTER TABLE business_user ADD COLUMN role_id UUID REFERENCES business_role(id);

INSERT INTO app_permission (id, code)
SELECT gen_random_uuid(), v
FROM unnest(
  ARRAY[
    'PRODUCTS_CREATE','PRODUCTS_READ','PRODUCTS_UPDATE','PRODUCTS_DELETE',
    'CATEGORIES_CREATE','CATEGORIES_READ','CATEGORIES_UPDATE','CATEGORIES_DELETE',
    'STOCK_READ','STOCK_INIT','STOCK_ADJUST',
    'CLIENTS_CREATE','CLIENTS_READ','CLIENTS_UPDATE','CLIENTS_DELETE',
    'SUPPLIERS_CREATE','SUPPLIERS_READ','SUPPLIERS_UPDATE','SUPPLIERS_DELETE',
    'PURCHASE_ORDERS_CREATE','PURCHASE_ORDERS_READ','PURCHASE_ORDERS_UPDATE','PURCHASE_ORDERS_DELETE',
    'SALES_CREATE','SALES_READ','SALES_UPDATE','SALES_DELETE',
    'EXPENSES_CREATE','EXPENSES_READ','EXPENSES_UPDATE','EXPENSES_DELETE',
    'DELIVERY_COURIERS_CREATE','DELIVERY_COURIERS_READ','DELIVERY_COURIERS_UPDATE','DELIVERY_COURIERS_DELETE',
    'STORES_CREATE','STORES_READ','STORES_UPDATE','STORES_DELETE',
    'GLOBAL_VIEW_READ',
    'SUBSCRIPTION_READ','SUBSCRIPTION_UPDATE',
    'BUSINESS_USERS_CREATE','BUSINESS_USERS_READ','BUSINESS_USERS_UPDATE','BUSINESS_USERS_DELETE',
    'API_KEYS_CREATE','API_KEYS_READ','API_KEYS_DELETE',
    'WEBHOOKS_CREATE','WEBHOOKS_READ','WEBHOOKS_UPDATE','WEBHOOKS_DELETE'
  ]
) AS v;

INSERT INTO business_role (id, business_id, code, name, is_system)
SELECT gen_random_uuid(), b.id, x.code, x.name, true
FROM business b
CROSS JOIN (
  VALUES
    ('PROPRIETAIRE', 'Propriétaire'),
    ('GESTIONNAIRE', 'Gestionnaire'),
    ('CAISSIER', 'Caissier')
) AS x(code, name);

INSERT INTO business_role_permission (role_id, permission_id)
SELECT br.id, ap.id
FROM business_role br
CROSS JOIN app_permission ap
WHERE br.code = 'PROPRIETAIRE';

INSERT INTO business_role_permission (role_id, permission_id)
SELECT br.id, ap.id
FROM business_role br
JOIN app_permission ap ON ap.code NOT IN ('SUBSCRIPTION_UPDATE', 'BUSINESS_USERS_DELETE')
WHERE br.code = 'GESTIONNAIRE';

INSERT INTO business_role_permission (role_id, permission_id)
SELECT br.id, ap.id
FROM business_role br
JOIN app_permission ap
  ON ap.code IN (
    'PRODUCTS_READ','CATEGORIES_READ','STOCK_READ','CLIENTS_READ','STORES_READ',
    'SALES_CREATE','SALES_READ','SALES_UPDATE','SALES_DELETE'
  )
WHERE br.code = 'CAISSIER';

UPDATE business_user bu
SET role_id = br.id
FROM business_role br
WHERE br.business_id = bu.business_id
  AND (
    (LOWER(TRIM(bu.role)) IN ('proprietaire', 'propriétaire') AND br.code = 'PROPRIETAIRE')
    OR (LOWER(TRIM(bu.role)) = 'gestionnaire' AND br.code = 'GESTIONNAIRE')
    OR (LOWER(TRIM(bu.role)) = 'caissier' AND br.code = 'CAISSIER')
  );

UPDATE business_user bu
SET role_id = br.id
FROM business_role br
WHERE br.business_id = bu.business_id
  AND br.code = 'CAISSIER'
  AND bu.role_id IS NULL;

ALTER TABLE business_user ALTER COLUMN role_id SET NOT NULL;

ALTER TABLE business_user DROP COLUMN role;

CREATE INDEX idx_business_role_business_id ON business_role(business_id);
CREATE INDEX idx_business_user_role_id ON business_user(role_id);
