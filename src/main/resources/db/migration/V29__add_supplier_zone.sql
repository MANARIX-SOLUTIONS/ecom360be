-- Add zone column to supplier
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS zone VARCHAR(100);
