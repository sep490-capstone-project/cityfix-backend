-- V11__add_unique_constraints_and_partial_indexes.sql

-- 1. category: partial unique index for name
CREATE UNIQUE INDEX uk_category_name_active ON "category" (name) WHERE is_deleted = false;

-- 2. user_account: drop existing unique constraints on email and phone_number
-- (PostgreSQL automatically names UNIQUE constraints table_column_key, e.g., user_account_email_key)
ALTER TABLE "user_account" DROP CONSTRAINT IF EXISTS user_account_email_key;
ALTER TABLE "user_account" DROP CONSTRAINT IF EXISTS user_account_phone_number_key;

-- 3. user_account: partial unique indexes for email and phone_number
CREATE UNIQUE INDEX uk_user_email_active ON "user_account" (email) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_user_phone_active ON "user_account" (phone_number) WHERE is_deleted = false;
