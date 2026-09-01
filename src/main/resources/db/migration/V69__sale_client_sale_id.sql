-- Idempotent POS / offline outbox: one sale per client-generated id per business.

ALTER TABLE sale ADD COLUMN IF NOT EXISTS client_sale_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sale_business_client_sale_id
    ON sale (business_id, client_sale_id)
    WHERE client_sale_id IS NOT NULL;
