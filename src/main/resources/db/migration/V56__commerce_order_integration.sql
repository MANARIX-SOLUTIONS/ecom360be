-- Connexions « commandes web » (sources externes) + journal d’ingestion (idempotence, audit)

CREATE TABLE commerce_connection (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
  store_id UUID NOT NULL REFERENCES store(id) ON DELETE CASCADE,
  source_type VARCHAR(64) NOT NULL,
  label VARCHAR(200) NOT NULL,
  incoming_token VARCHAR(64) NOT NULL UNIQUE,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_commerce_connection_business ON commerce_connection(business_id);
CREATE INDEX idx_commerce_connection_store ON commerce_connection(store_id);

CREATE TABLE commerce_order_ingestion_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  connection_id UUID NOT NULL REFERENCES commerce_connection(id) ON DELETE CASCADE,
  business_id UUID NOT NULL REFERENCES business(id) ON DELETE CASCADE,
  source_type VARCHAR(64) NOT NULL,
  external_order_id VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  raw_payload TEXT,
  error_message TEXT,
  sale_id UUID REFERENCES sale(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_commerce_ingestion_connection_external
  ON commerce_order_ingestion_log(connection_id, external_order_id);
CREATE INDEX idx_commerce_ingestion_business_created
  ON commerce_order_ingestion_log(business_id, created_at DESC);
