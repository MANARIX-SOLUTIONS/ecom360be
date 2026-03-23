-- Livraison: gestion des livreurs (module PRO)
-- 1) Add feature_delivery_couriers to plan (PRO + Business only)
ALTER TABLE plan
    ADD COLUMN IF NOT EXISTS feature_delivery_couriers BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE plan SET feature_delivery_couriers = false, updated_at = NOW() WHERE slug = 'starter';
UPDATE plan SET feature_delivery_couriers = true, updated_at = NOW() WHERE slug = 'business';
UPDATE plan SET feature_delivery_couriers = true, updated_at = NOW() WHERE slug = 'pro';

-- 2) Table livreur (courier) per business
CREATE TABLE IF NOT EXISTS livreur (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_livreur_active ON livreur(business_id, is_active);
CREATE INDEX IF NOT EXISTS idx_livreur_business_id ON livreur(business_id);

COMMENT ON TABLE livreur IS 'Livreurs / coursiers pour la livraison (plan PRO)';
