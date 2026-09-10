-- Enable postgis extension
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Update roles as requested
UPDATE "roles" SET name = 'RESIDENT' WHERE name = 'CITIZEN';
UPDATE "roles" SET name = 'STAFF' WHERE name = 'GOVERNMENT';

-- Insert new roles if they don't exist, in case V1 didn't run or this runs fresh
INSERT INTO "roles" (name) VALUES ('RESIDENT'), ('STAFF') ON CONFLICT (name) DO NOTHING;

-- -----------------------------------------------------
-- Table: tickets
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS "tickets" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    image_url VARCHAR(255),
    category VARCHAR(100),
    severity VARCHAR(50),
    danger_level INT,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    vote_count INT NOT NULL DEFAULT 0,
    location geometry(Point, 4326),
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_created_by FOREIGN KEY (created_by) REFERENCES "users"(id) ON DELETE RESTRICT
);

-- Index for spatial query on location
CREATE INDEX IF NOT EXISTS idx_tickets_location ON "tickets" USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON "tickets" (status);

-- -----------------------------------------------------
-- Table: ticket_subscribers
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS "ticket_subscribers" (
    ticket_id UUID NOT NULL,
    user_id UUID NOT NULL,
    subscribed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_id, user_id),
    CONSTRAINT fk_ts_ticket FOREIGN KEY (ticket_id) REFERENCES "tickets"(id) ON DELETE CASCADE,
    CONSTRAINT fk_ts_user FOREIGN KEY (user_id) REFERENCES "users"(id) ON DELETE CASCADE
);
