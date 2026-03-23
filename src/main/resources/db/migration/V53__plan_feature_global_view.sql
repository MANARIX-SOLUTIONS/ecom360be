-- Vue globale : réservée aux plans Pro et Business
ALTER TABLE plan
    ADD COLUMN IF NOT EXISTS feature_global_view BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE plan SET feature_global_view = false, updated_at = NOW() WHERE slug = 'starter';
UPDATE plan SET feature_global_view = true, updated_at = NOW() WHERE slug = 'pro';
UPDATE plan SET feature_global_view = true, updated_at = NOW() WHERE slug = 'business';

COMMENT ON COLUMN plan.feature_global_view IS 'Vue globale de toutes les boutiques (Pro, Business)';
