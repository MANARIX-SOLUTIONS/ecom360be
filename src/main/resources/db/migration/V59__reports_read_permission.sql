-- Permission dédiée écran Rapports (navigation + API dashboard utilisée par /reports)

INSERT INTO app_permission (id, code, label, category, sort_order)
SELECT gen_random_uuid(),
       'REPORTS_READ',
       'Rapports — Consulter',
       'reports',
       275
WHERE NOT EXISTS (SELECT 1 FROM app_permission WHERE code = 'REPORTS_READ');

INSERT INTO business_role_permission (role_id, permission_id)
SELECT br.id, ap.id
FROM business_role br
         JOIN app_permission ap ON ap.code = 'REPORTS_READ'
WHERE br.code IN ('PROPRIETAIRE', 'GESTIONNAIRE', 'CAISSIER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
