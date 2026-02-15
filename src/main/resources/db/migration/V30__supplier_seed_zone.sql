-- Set zone for seeded supplier (address was "Zone 4")
UPDATE supplier SET zone = 'Zone 4' WHERE id = '70000001-0000-4000-8000-000000000001' AND zone IS NULL;
