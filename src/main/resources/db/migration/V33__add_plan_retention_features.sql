-- Add retention-focused plan features (PLANS_ABONNEMENTS.md)
-- data_retention_months: 0 = illimité, 3 = Starter, 12 = Pro
-- feature_stock_alerts: alertes stock bas (Pro, Business)

ALTER TABLE plan
    ADD COLUMN IF NOT EXISTS data_retention_months INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS feature_stock_alerts BOOLEAN NOT NULL DEFAULT FALSE;

-- Starter: 3 mois historique, pas d'alertes stock
UPDATE plan SET
    data_retention_months = 3,
    feature_stock_alerts = false,
    updated_at = NOW()
WHERE slug = 'starter';

-- Pro: 12 mois historique, alertes stock
UPDATE plan SET
    data_retention_months = 12,
    feature_stock_alerts = true,
    updated_at = NOW()
WHERE slug = 'pro';

-- Business: illimité (0), alertes stock
UPDATE plan SET
    data_retention_months = 0,
    feature_stock_alerts = true,
    updated_at = NOW()
WHERE slug = 'business';
