-- Add missing columns from BaseEntity to tickets table
ALTER TABLE "tickets"
ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN deleted_at TIMESTAMP;
