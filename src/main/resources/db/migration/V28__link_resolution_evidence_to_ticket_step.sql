-- V28: Link resolution_evidence to ticket_step safely without data loss

-- 1. Add ticket_step_id column allowing null for data migration
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

-- 3. Safely archive orphaned evidence into a backup table before schema alteration
CREATE TABLE IF NOT EXISTS "legacy_orphan_evidence" (
    evidence_id UUID PRIMARY KEY,
    ticket_id UUID,
    staff_id UUID,
    image_url VARCHAR(500),
    resolution_note TEXT,
    created_at TIMESTAMP
);

INSERT INTO "legacy_orphan_evidence" (evidence_id, ticket_id, staff_id, image_url, resolution_note, created_at)
SELECT evidence_id, ticket_id, staff_id, image_url, resolution_note, created_at
FROM "resolution_evidence"
WHERE ticket_step_id IS NULL
ON CONFLICT (evidence_id) DO NOTHING;

-- 4. Preserve old ticket_id by renaming to legacy_ticket_id instead of dropping it
ALTER TABLE "resolution_evidence" DROP CONSTRAINT IF EXISTS fk_re_ticket;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'resolution_evidence' AND column_name = 'ticket_id'
    ) THEN
        ALTER TABLE "resolution_evidence" RENAME COLUMN ticket_id TO legacy_ticket_id;
    END IF;
END $$;

-- 5. Foreign Key to ticket_step (allows NULL for historical orphan records without data deletion)
ALTER TABLE "resolution_evidence" DROP CONSTRAINT IF EXISTS fk_re_ticket_step;
ALTER TABLE "resolution_evidence" 
    ADD CONSTRAINT fk_re_ticket_step FOREIGN KEY (ticket_step_id) REFERENCES "ticket_step"(id) ON DELETE CASCADE;

-- 6. Add index for query performance on ticket_step_id
CREATE INDEX IF NOT EXISTS idx_re_ticket_step_id ON "resolution_evidence"(ticket_step_id);
