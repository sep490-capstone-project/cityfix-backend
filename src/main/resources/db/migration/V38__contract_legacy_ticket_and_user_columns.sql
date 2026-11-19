-- Migration V38: Contract legacy columns from ticket and user_account tables

-- 1. Drop legacy spatial index and redundant columns from ticket
DROP INDEX IF EXISTS idx_ticket_location;
DROP INDEX IF EXISTS idx_tickets_location;

ALTER TABLE "ticket"
    DROP COLUMN IF EXISTS "location",
    DROP COLUMN IF EXISTS "address",
    DROP COLUMN IF EXISTS "location_method",
    DROP COLUMN IF EXISTS "priority",
    DROP COLUMN IF EXISTS "priority_reasoning",
    DROP COLUMN IF EXISTS "is_life_threatening",
    DROP COLUMN IF EXISTS "needs_manual_review",
    DROP COLUMN IF EXISTS "review_note",
    DROP COLUMN IF EXISTS "deadline";

-- 2. Drop legacy foreign keys, unique index, and redundant columns from user_account
ALTER TABLE "user_account" DROP CONSTRAINT IF EXISTS fk_user_role;
ALTER TABLE "user_account" DROP CONSTRAINT IF EXISTS fk_user_department;

DROP INDEX IF EXISTS uk_user_phone_active;

ALTER TABLE "user_account"
    DROP COLUMN IF EXISTS "role_id",
    DROP COLUMN IF EXISTS "department_id",
    DROP COLUMN IF EXISTS "full_name",
    DROP COLUMN IF EXISTS "phone_number",
    DROP COLUMN IF EXISTS "province",
    DROP COLUMN IF EXISTS "ward",
    DROP COLUMN IF EXISTS "address";
