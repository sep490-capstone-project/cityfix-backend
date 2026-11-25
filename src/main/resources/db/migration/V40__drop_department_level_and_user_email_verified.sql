-- Migration V40: Drop level from department and email_verified from user_account
ALTER TABLE "department" DROP COLUMN IF EXISTS "level";
ALTER TABLE "user_account" DROP COLUMN IF EXISTS "email_verified";

