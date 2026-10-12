-- V19__cleanup_legacy_assignment_and_add_ticket_duration.sql
-- Drop legacy ticket_assignment table, remove is_dispatcher column from user_account, and add duration to ticket

DROP TABLE IF EXISTS ticket_assignment CASCADE;

ALTER TABLE user_account DROP COLUMN IF EXISTS is_dispatcher;

ALTER TABLE ticket ADD COLUMN IF NOT EXISTS duration INT;
