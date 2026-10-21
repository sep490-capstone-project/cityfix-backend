-- Phase 2 (Contract): Drop legacy columns after application code has fully transitioned to user_notification
-- Run this migration when new code is stable and no legacy instances need user_id or is_read.

ALTER TABLE notifications DROP COLUMN IF EXISTS user_id;
ALTER TABLE notifications DROP COLUMN IF EXISTS is_read;
