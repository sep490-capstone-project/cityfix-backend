-- V11__add_unique_constraints_and_partial_indexes.sql

-- 1. Handle existing active duplicated categories by appending a substring of their UUID to their name
UPDATE "category"
SET name = name || '_' || substr(category_id::text, 1, 8)
WHERE category_id IN (
    SELECT category_id FROM (
        SELECT category_id,
               ROW_NUMBER() OVER (PARTITION BY name ORDER BY created_at ASC) as rnum
        FROM "category"
        WHERE is_deleted = false
    ) t
    WHERE t.rnum > 1
);

-- 2. category: partial unique index for name
CREATE UNIQUE INDEX uk_category_name_active ON "category" (name) WHERE is_deleted = false;

-- 3. user_account: dynamically drop existing unique constraints on email and phone_number
DO $$
DECLARE
    constraint_name_email TEXT;
    constraint_name_phone TEXT;
BEGIN
    SELECT tc.constraint_name INTO constraint_name_email
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage AS ccu USING (constraint_schema, constraint_name)
    WHERE tc.table_name = 'user_account' AND ccu.column_name = 'email' AND tc.constraint_type = 'UNIQUE'
    LIMIT 1;

    IF constraint_name_email IS NOT NULL THEN
        EXECUTE 'ALTER TABLE "user_account" DROP CONSTRAINT ' || constraint_name_email;
    END IF;

    SELECT tc.constraint_name INTO constraint_name_phone
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage AS ccu USING (constraint_schema, constraint_name)
    WHERE tc.table_name = 'user_account' AND ccu.column_name = 'phone_number' AND tc.constraint_type = 'UNIQUE'
    LIMIT 1;

    IF constraint_name_phone IS NOT NULL THEN
        EXECUTE 'ALTER TABLE "user_account" DROP CONSTRAINT ' || constraint_name_phone;
    END IF;
END $$;

-- 4. user_account: partial unique indexes for email and phone_number
CREATE UNIQUE INDEX uk_user_email_active ON "user_account" (email) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_user_phone_active ON "user_account" (phone_number) WHERE is_deleted = false;
