-- Migration V35: Drop severity column and add priority_reasoning to ticket table
ALTER TABLE "ticket" DROP COLUMN IF EXISTS "severity";
ALTER TABLE "ticket" ADD COLUMN IF NOT EXISTS "priority_reasoning" TEXT;
