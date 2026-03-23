-- Add request_id to audit_log for correlation with application logs
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_id VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_audit_log_request_id ON audit_log(request_id) WHERE request_id IS NOT NULL;
