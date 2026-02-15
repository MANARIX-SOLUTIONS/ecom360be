-- 360 PME Commerce - Initial Schema
-- PostgreSQL with UUIDs, timestamptz, integer amounts (FCFA)

-- Platform / Multi-Tenant Core
CREATE TABLE platform_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(255) NOT NULL UNIQUE,
    value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    price_monthly INTEGER NOT NULL,
    price_yearly INTEGER NOT NULL,
    max_users INTEGER NOT NULL DEFAULT 0,
    max_stores INTEGER NOT NULL DEFAULT 0,
    max_products INTEGER NOT NULL DEFAULT 0,
    max_sales_per_month INTEGER NOT NULL DEFAULT 0,
    max_clients INTEGER NOT NULL DEFAULT 0,
    max_suppliers INTEGER NOT NULL DEFAULT 0,
    feature_expenses BOOLEAN NOT NULL DEFAULT FALSE,
    feature_reports BOOLEAN NOT NULL DEFAULT FALSE,
    feature_advanced_reports BOOLEAN NOT NULL DEFAULT FALSE,
    feature_multi_payment BOOLEAN NOT NULL DEFAULT FALSE,
    feature_export_pdf BOOLEAN NOT NULL DEFAULT FALSE,
    feature_export_excel BOOLEAN NOT NULL DEFAULT FALSE,
    feature_client_credits BOOLEAN NOT NULL DEFAULT FALSE,
    feature_supplier_tracking BOOLEAN NOT NULL DEFAULT FALSE,
    feature_role_management BOOLEAN NOT NULL DEFAULT FALSE,
    feature_api BOOLEAN NOT NULL DEFAULT FALSE,
    feature_custom_branding BOOLEAN NOT NULL DEFAULT FALSE,
    feature_priority_support BOOLEAN NOT NULL DEFAULT FALSE,
    feature_account_manager BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE business (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50),
    address TEXT,
    logo_url VARCHAR(500),
    tax_id VARCHAR(50),
    currency VARCHAR(10) NOT NULL DEFAULT 'XOF',
    locale VARCHAR(10) NOT NULL DEFAULT 'fr',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    trial_ends_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    plan_id UUID NOT NULL REFERENCES plan(id),
    billing_cycle VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_period_start DATE NOT NULL,
    current_period_end DATE NOT NULL,
    cancelled_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    subscription_id UUID NOT NULL REFERENCES subscription(id),
    number VARCHAR(50) NOT NULL UNIQUE,
    amount INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(50),
    due_date DATE NOT NULL,
    paid_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Users & Auth
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50),
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    locale VARCHAR(10) NOT NULL DEFAULT 'fr',
    is_platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE business_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    invited_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(business_id, user_id)
);

CREATE TABLE session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(50),
    user_agent TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE password_reset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Stores
CREATE TABLE store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Products & Inventory
CREATE TABLE category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    name VARCHAR(255) NOT NULL,
    color VARCHAR(20),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    category_id UUID REFERENCES category(id),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(100),
    barcode VARCHAR(100),
    description TEXT,
    cost_price INTEGER NOT NULL,
    sale_price INTEGER NOT NULL,
    unit VARCHAR(50) NOT NULL DEFAULT 'pièce',
    image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE product_store_stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id),
    store_id UUID NOT NULL REFERENCES store(id),
    quantity INTEGER NOT NULL DEFAULT 0,
    min_stock INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(product_id, store_id)
);

CREATE TABLE stock_movement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product(id),
    store_id UUID NOT NULL REFERENCES store(id),
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    reference VARCHAR(100),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Clients & Credits
CREATE TABLE client (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    address TEXT,
    notes TEXT,
    credit_balance INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE client_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES client(id),
    store_id UUID NOT NULL REFERENCES store(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount INTEGER NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Suppliers & Purchases
CREATE TABLE supplier (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    address TEXT,
    balance INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE purchase_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    supplier_id UUID NOT NULL REFERENCES supplier(id),
    store_id UUID NOT NULL REFERENCES store(id),
    user_id UUID NOT NULL REFERENCES users(id),
    reference VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    total_amount INTEGER NOT NULL,
    expected_date DATE,
    received_date DATE,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE purchase_order_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_order(id),
    product_id UUID NOT NULL REFERENCES product(id),
    quantity INTEGER NOT NULL,
    unit_cost INTEGER NOT NULL,
    line_total INTEGER NOT NULL
);

CREATE TABLE supplier_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES supplier(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount INTEGER NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Sales / POS
CREATE TABLE sale (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    store_id UUID NOT NULL REFERENCES store(id),
    user_id UUID NOT NULL REFERENCES users(id),
    client_id UUID REFERENCES client(id),
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    payment_method VARCHAR(50) NOT NULL,
    subtotal INTEGER NOT NULL,
    discount_amount INTEGER NOT NULL DEFAULT 0,
    total INTEGER NOT NULL,
    amount_received INTEGER,
    change_given INTEGER,
    status VARCHAR(20) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sale_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL REFERENCES sale(id),
    product_id UUID NOT NULL REFERENCES product(id),
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price INTEGER NOT NULL,
    line_total INTEGER NOT NULL
);

-- Expenses
CREATE TABLE expense_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE expense (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    store_id UUID REFERENCES store(id),
    user_id UUID NOT NULL REFERENCES users(id),
    category_id UUID NOT NULL REFERENCES expense_category(id),
    amount INTEGER NOT NULL,
    description TEXT,
    expense_date DATE NOT NULL,
    receipt_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Notifications
CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID REFERENCES business(id),
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    action_url VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit / Logs
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID REFERENCES business(id),
    user_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    changes JSONB,
    ip_address VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Feature Flags
CREATE TABLE feature_flag (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(100) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL,
    description TEXT,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Extension Points
CREATE TABLE webhook (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    url VARCHAR(500) NOT NULL,
    events VARCHAR(500) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE api_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES business(id),
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL,
    permissions VARCHAR(100) NOT NULL,
    expires_at DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for high-frequency lookups
CREATE INDEX idx_sale_business_store ON sale (business_id, store_id, created_at DESC);
CREATE INDEX idx_sale_client ON sale (client_id) WHERE client_id IS NOT NULL;
CREATE INDEX idx_sale_line_sale ON sale_line (sale_id);
CREATE INDEX idx_product_business ON product (business_id, is_active);
CREATE INDEX idx_product_store_stock ON product_store_stock (product_id, store_id);
CREATE INDEX idx_stock_movement_product ON stock_movement (product_id, created_at DESC);
CREATE INDEX idx_expense_business_date ON expense (business_id, expense_date DESC);
CREATE INDEX idx_client_business ON client (business_id, is_active);
CREATE INDEX idx_supplier_business ON supplier (business_id, is_active);
CREATE INDEX idx_notification_user ON notification (user_id, is_read, created_at DESC);
CREATE INDEX idx_audit_log_business ON audit_log (business_id, created_at DESC);
CREATE INDEX idx_business_user_lookup ON business_user (user_id, business_id);
CREATE INDEX idx_subscription_business ON subscription (business_id, status);
