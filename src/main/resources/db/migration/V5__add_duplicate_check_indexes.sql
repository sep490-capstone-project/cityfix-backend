-- Ensure GiST index on location spatial column and add composite index for duplicate detection
CREATE INDEX IF NOT EXISTS idx_tickets_location ON "tickets" USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_tickets_category_status ON "tickets" (category_id, status);
