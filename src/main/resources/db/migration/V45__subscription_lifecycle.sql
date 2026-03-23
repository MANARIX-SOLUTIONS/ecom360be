-- Subscription lifecycle: cancel at period end, support for grace period
ALTER TABLE subscription ADD COLUMN IF NOT EXISTS cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subscription ADD COLUMN IF NOT EXISTS expired_at DATE;
ALTER TABLE subscription ADD COLUMN IF NOT EXISTS grace_period_ends_at DATE;

-- Index for expiration job (find trialing/active that need expiration)
CREATE INDEX IF NOT EXISTS idx_subscription_status_period
  ON subscription (status, current_period_end)
  WHERE status IN ('trialing', 'active');
