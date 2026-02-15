-- Make demo user a platform admin (for dev/testing admin endpoints)
UPDATE users SET is_platform_admin = true WHERE email = 'demo@ecom360.local';
