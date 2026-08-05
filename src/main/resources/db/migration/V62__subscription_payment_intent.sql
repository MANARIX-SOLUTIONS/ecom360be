-- US-01: subscription checkout intents (PayDunya / Wave / Orange Money)

CREATE TABLE subscription_payment_intent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    plan_id UUID NOT NULL REFERENCES plan(id),
    billing_cycle VARCHAR(20) NOT NULL,
    amount INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'XOF',
    provider VARCHAR(50) NOT NULL DEFAULT 'paydunya',
    preferred_channel VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    external_token VARCHAR(255),
    external_ref VARCHAR(255),
    checkout_url VARCHAR(1000),
    return_url VARCHAR(1000),
    subscription_id UUID REFERENCES subscription(id),
    invoice_id UUID,
    paid_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    metadata TEXT,
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_payment_intent_external_token UNIQUE (external_token)
);

CREATE INDEX idx_spi_business_created
    ON subscription_payment_intent (business_id, created_at DESC);

CREATE INDEX idx_spi_status_created
    ON subscription_payment_intent (status, created_at DESC);

ALTER TABLE invoice
    ADD COLUMN IF NOT EXISTS payment_intent_id UUID,
    ADD COLUMN IF NOT EXISTS provider VARCHAR(50),
    ADD COLUMN IF NOT EXISTS external_ref VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_invoice_payment_intent
    ON invoice (payment_intent_id);
