-- Demandes de démo : inscription soumise à validation admin avant création du compte.

CREATE TABLE demo_request (
    id UUID PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    business_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    message TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by_user_id UUID REFERENCES users (id),
    rejection_reason TEXT
);

CREATE INDEX idx_demo_request_status ON demo_request (status);
CREATE INDEX idx_demo_request_created ON demo_request (created_at DESC);

-- Une seule demande en attente par email (insensible à la casse).
CREATE UNIQUE INDEX uq_demo_request_pending_email ON demo_request (LOWER(email))
    WHERE status = 'pending';
