-- 1. Create categories table
CREATE TABLE IF NOT EXISTS "categories" (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- 2. Modify users table
ALTER TABLE "users" 
    RENAME COLUMN id TO user_id;

ALTER TABLE "users" 
    RENAME COLUMN password TO password_hash;

ALTER TABLE "users" 
    DROP CONSTRAINT IF EXISTS fk_user_role;

ALTER TABLE "users" 
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'RESIDENT' NOT NULL;

ALTER TABLE "users" 
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;

ALTER TABLE "users" 
    DROP COLUMN IF EXISTS role_id;

-- Drop obsolete roles table
DROP TABLE IF EXISTS "roles" CASCADE;

-- 3. Modify tickets table
ALTER TABLE "tickets" RENAME COLUMN id TO ticket_id;
ALTER TABLE "tickets" RENAME COLUMN created_by TO user_id;

ALTER TABLE "tickets" ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE "tickets" DROP CONSTRAINT IF EXISTS fk_ticket_category;
ALTER TABLE "tickets" ADD CONSTRAINT fk_ticket_category FOREIGN KEY (category_id) REFERENCES "categories"(category_id) ON DELETE SET NULL;

ALTER TABLE "tickets" ALTER COLUMN danger_level TYPE VARCHAR(20) USING danger_level::varchar;
ALTER TABLE "tickets" ADD COLUMN IF NOT EXISTS location_method VARCHAR(20);
ALTER TABLE "tickets" ADD COLUMN IF NOT EXISTS address VARCHAR(255);

-- 4. Create report_images and migrate data
CREATE TABLE IF NOT EXISTS "report_images" (
    media_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ri_ticket FOREIGN KEY (ticket_id) REFERENCES "tickets"(ticket_id) ON DELETE CASCADE
);

INSERT INTO "report_images" (ticket_id, image_url)
SELECT ticket_id, image_url FROM "tickets" WHERE image_url IS NOT NULL;

ALTER TABLE "tickets" DROP COLUMN IF EXISTS image_url;
ALTER TABLE "tickets" DROP COLUMN IF EXISTS vote_count;

-- 5. Ticket Votes
DROP TABLE IF EXISTS "ticket_subscribers" CASCADE;

CREATE TABLE IF NOT EXISTS "ticket_votes" (
    vote_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tv_ticket FOREIGN KEY (ticket_id) REFERENCES "tickets"(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_tv_user FOREIGN KEY (user_id) REFERENCES "users"(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_ticket_user_vote UNIQUE (ticket_id, user_id)
);

-- 6. AI Analysis
CREATE TABLE IF NOT EXISTS "ai_analysis" (
    analysis_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    category_id UUID,
    severity VARCHAR(20),
    danger_level VARCHAR(20),
    confidence_score DECIMAL(5,2),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_accepted_by_human BOOLEAN,
    CONSTRAINT fk_ai_ticket FOREIGN KEY (ticket_id) REFERENCES "tickets"(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_category FOREIGN KEY (category_id) REFERENCES "categories"(category_id) ON DELETE SET NULL
);

-- 7. Auth Sessions
CREATE TABLE IF NOT EXISTS "auth_sessions" (
    access_token VARCHAR(500) PRIMARY KEY,
    refresh_token VARCHAR(500),
    expires_at TIMESTAMP NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES "users"(user_id) ON DELETE CASCADE
);
