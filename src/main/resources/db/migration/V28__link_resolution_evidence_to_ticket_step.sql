-- V28: Link resolution_evidence to ticket_step instead of ticket

-- 1. Add ticket_step_id column allowing null temporarily for data migration
ALTER TABLE "resolution_evidence" ADD COLUMN IF NOT EXISTS ticket_step_id UUID;

-- 2. Safely backfill existing resolution_evidence records to corresponding ticket_step
UPDATE "resolution_evidence" re
SET ticket_step_id = (
    SELECT ts.id 
    FROM "ticket_step" ts 
    WHERE ts.ticket_id = re.ticket_id 
    ORDER BY ts.order_index ASC NULLS LAST, ts.created_at ASC 
    LIMIT 1
)
WHERE re.ticket_step_id IS NULL;

-- 3. Delete any orphaned evidence where no ticket_step exists
DELETE FROM "resolution_evidence" WHERE ticket_step_id IS NULL;

-- 4. Set ticket_step_id NOT NULL and add foreign key constraint
ALTER TABLE "resolution_evidence" ALTER COLUMN ticket_step_id SET NOT NULL;
ALTER TABLE "resolution_evidence" 
    ADD CONSTRAINT fk_re_ticket_step FOREIGN KEY (ticket_step_id) REFERENCES "ticket_step"(id) ON DELETE CASCADE;

-- 5. Drop old foreign key constraint and ticket_id column
ALTER TABLE "resolution_evidence" DROP CONSTRAINT IF EXISTS fk_re_ticket;
ALTER TABLE "resolution_evidence" DROP COLUMN IF EXISTS ticket_id;

-- 6. Add index for query performance on ticket_step_id
CREATE INDEX IF NOT EXISTS idx_re_ticket_step_id ON "resolution_evidence"(ticket_step_id);
