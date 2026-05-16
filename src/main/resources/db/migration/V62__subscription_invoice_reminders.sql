-- Idempotent tracking for subscription period-end reminders (J-7, J-3, J-1).
CREATE TABLE subscription_reminder_sent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscription(id) ON DELETE CASCADE,
    reminder_kind VARCHAR(32) NOT NULL,
    period_end DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_reminder UNIQUE (subscription_id, reminder_kind, period_end)
);

CREATE INDEX ix_subscription_reminder_period ON subscription_reminder_sent (period_end);

-- Idempotent tracking for unpaid invoice due-date reminders (when billing reminders enabled).
CREATE TABLE invoice_reminder_sent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    reminder_kind VARCHAR(32) NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_invoice_reminder UNIQUE (invoice_id, reminder_kind, due_date)
);
