-- Codes canoniques français (alignés UI / JWT) : remplace ADMIN / MANAGER / SELLER historiques.

UPDATE business_role
SET code = 'PROPRIETAIRE',
    name = 'Propriétaire'
WHERE code = 'ADMIN';

UPDATE business_role
SET code = 'GESTIONNAIRE'
WHERE code = 'MANAGER';

UPDATE business_role
SET code = 'CAISSIER'
WHERE code = 'SELLER';
