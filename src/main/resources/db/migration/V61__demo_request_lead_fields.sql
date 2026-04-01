-- Demande démo : mot de passe optionnel (défini après validation par lien), champs lead PME.

ALTER TABLE demo_request
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE demo_request
    ADD COLUMN job_title VARCHAR(128);

ALTER TABLE demo_request
    ADD COLUMN city VARCHAR(128);

ALTER TABLE demo_request
    ADD COLUMN sector VARCHAR(128);
