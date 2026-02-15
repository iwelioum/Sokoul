-- Fix ALL migration mismatches
-- Delete all 202602* migration records that were modified after being recorded

DELETE FROM _sqlx_migrations WHERE version >= 20260214000000;

-- Verify deletion - you should only see 20240101000000
SELECT version, description, success FROM _sqlx_migrations ORDER BY version DESC;
