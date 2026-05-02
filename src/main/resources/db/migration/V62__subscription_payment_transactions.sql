CREATE TABLE payment_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    subscription_id UUID NOT NULL REFERENCES subscription(id),
    invoice_id UUID NOT NULL REFERENCES invoice(id),
    provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(255) NOT NULL,
    amount INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'XOF',
    status VARCHAR(20) NOT NULL,
    plan_slug VARCHAR(50) NOT NULL,
    billing_cycle VARCHAR(20) NOT NULL,
    checkout_url VARCHAR(1000),
    failure_reason VARCHAR(255),
    paid_at TIMESTAMPTZ,
    raw_callback TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_transaction_provider_reference UNIQUE (provider, provider_reference)
);

CREATE INDEX idx_payment_transaction_business_created
    ON payment_transaction (business_id, created_at DESC);

CREATE INDEX idx_payment_transaction_subscription
    ON payment_transaction (subscription_id);
