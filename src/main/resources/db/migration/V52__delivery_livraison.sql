-- Livraisons: enregistrement des colis par livreur (performance)
CREATE TABLE IF NOT EXISTS livraison (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
    courier_id UUID NOT NULL REFERENCES livreur(id) ON DELETE CASCADE,
    sale_id UUID REFERENCES sale(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'delivered' CHECK (status IN ('delivered', 'failed', 'cancelled')),
    parcels_count INTEGER NOT NULL DEFAULT 1 CHECK (parcels_count > 0),
    delivered_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_livraison_business_id ON livraison(business_id);
CREATE INDEX IF NOT EXISTS idx_livraison_courier_id ON livraison(courier_id);
CREATE INDEX IF NOT EXISTS idx_livraison_delivered_at ON livraison(courier_id, delivered_at);

COMMENT ON TABLE livraison IS 'Livraisons (colis) par livreur pour indicateurs de performance';
