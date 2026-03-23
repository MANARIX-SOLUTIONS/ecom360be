-- Affectation des employés (business_user) aux boutiques (store) - multi-boutique
CREATE TABLE business_user_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_user_id UUID NOT NULL REFERENCES business_user(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES store(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(business_user_id, store_id)
);

CREATE INDEX idx_business_user_store_user ON business_user_store(business_user_id);
CREATE INDEX idx_business_user_store_store ON business_user_store(store_id);
