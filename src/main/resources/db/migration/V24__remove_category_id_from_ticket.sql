-- Migration V24: Xoa khoa ngoai, chi muc va cot category_id khoi bang ticket
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_category;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS ticket_category_id_fkey;
DROP INDEX IF EXISTS idx_tickets_category_status;
DROP INDEX IF EXISTS idx_ticket_category_id;
ALTER TABLE ticket DROP COLUMN IF EXISTS category_id;
