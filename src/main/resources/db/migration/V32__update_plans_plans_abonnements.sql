-- Update plans to match PLANS_ABONNEMENTS.md (15k, 25k, 35k — stratégie 100–200 clients)
-- Starter: 2 users, 1 store, 500 sales/mo, 100 products, 50 clients, 10 suppliers
UPDATE plan SET
    price_monthly = 15000,
    price_yearly = 120000,
    max_users = 2,
    max_stores = 1,
    max_products = 100,
    max_sales_per_month = 500,
    max_clients = 50,
    max_suppliers = 10,
    feature_expenses = false,
    feature_reports = false,
    feature_advanced_reports = false,
    feature_multi_payment = false,
    feature_export_pdf = false,
    feature_export_excel = false,
    feature_client_credits = false,
    feature_supplier_tracking = false,
    feature_role_management = false,
    feature_api = false,
    feature_custom_branding = false,
    feature_priority_support = false,
    feature_account_manager = false,
    updated_at = NOW()
WHERE slug = 'starter';

-- Pro: 5 users, 3 stores, 2000 sales/mo, 500 products, unlimited clients/suppliers
UPDATE plan SET
    price_monthly = 25000,
    price_yearly = 250000,
    max_users = 5,
    max_stores = 3,
    max_products = 500,
    max_sales_per_month = 2000,
    max_clients = 0,
    max_suppliers = 0,
    feature_expenses = true,
    feature_reports = true,
    feature_advanced_reports = false,
    feature_multi_payment = true,
    feature_export_pdf = true,
    feature_export_excel = true,
    feature_client_credits = true,
    feature_supplier_tracking = true,
    feature_role_management = false,
    feature_api = false,
    feature_custom_branding = false,
    feature_priority_support = true,
    feature_account_manager = false,
    updated_at = NOW()
WHERE slug = 'pro';

-- Business: unlimited everything, all features
UPDATE plan SET
    price_monthly = 35000,
    price_yearly = 350000,
    max_users = 0,
    max_stores = 0,
    max_products = 0,
    max_sales_per_month = 0,
    max_clients = 0,
    max_suppliers = 0,
    feature_expenses = true,
    feature_reports = true,
    feature_advanced_reports = true,
    feature_multi_payment = true,
    feature_export_pdf = true,
    feature_export_excel = true,
    feature_client_credits = true,
    feature_supplier_tracking = true,
    feature_role_management = true,
    feature_api = true,
    feature_custom_branding = true,
    feature_priority_support = true,
    feature_account_manager = true,
    updated_at = NOW()
WHERE slug = 'business';
