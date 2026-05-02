ALTER TABLE demo_request
    ADD COLUMN preferred_plan_slug VARCHAR(50);

ALTER TABLE demo_request
    ADD COLUMN preferred_billing_cycle VARCHAR(20);
