-- Migration V20: Make order_index nullable and add is_deleted column to ticket_step table

ALTER TABLE ticket_step ALTER COLUMN order_index DROP NOT NULL;
ALTER TABLE ticket_step ALTER COLUMN order_index DROP DEFAULT;

ALTER TABLE ticket_step ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
