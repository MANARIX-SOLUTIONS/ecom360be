-- Trial used only once per business
ALTER TABLE business ADD COLUMN IF NOT EXISTS trial_used_at TIMESTAMPTZ;
