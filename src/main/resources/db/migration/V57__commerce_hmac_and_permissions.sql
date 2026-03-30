-- Permissions dédiées connexions commerce + secret HMAC par connexion

-- Enable pgcrypto extension if not already enabled
CREATE
EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO app_permission (id, code)
SELECT gen_random_uuid(), v
FROM unnest(
             ARRAY[
                 'COMMERCE_CONNECTIONS_CREATE',
             'COMMERCE_CONNECTIONS_READ',
             'COMMERCE_CONNECTIONS_UPDATE',
             'COMMERCE_CONNECTIONS_DELETE'
                 ]
     ) AS v ON CONFLICT (code) DO NOTHING;

INSERT INTO business_role_permission (role_id, permission_id)
SELECT br.id, ap.id
FROM business_role br
         JOIN app_permission ap
              ON ap.code IN (
                             'COMMERCE_CONNECTIONS_CREATE',
                             'COMMERCE_CONNECTIONS_READ',
                             'COMMERCE_CONNECTIONS_UPDATE',
                             'COMMERCE_CONNECTIONS_DELETE'
                  )
WHERE br.code IN ('PROPRIETAIRE', 'GESTIONNAIRE') ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE commerce_connection
    ADD COLUMN IF NOT EXISTS hmac_secret VARCHAR (128);

UPDATE commerce_connection
SET hmac_secret = lower(encode(gen_random_bytes(32), 'hex'))
WHERE hmac_secret IS NULL;

ALTER TABLE commerce_connection
    ALTER COLUMN hmac_secret SET NOT NULL;
