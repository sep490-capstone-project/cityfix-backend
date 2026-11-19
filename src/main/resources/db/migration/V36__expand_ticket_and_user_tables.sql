-- Migration V36: Expand ticket and user_account tables into 1-1 satellite tables

-- 1. Create ticket_location table (Spatial Data 1-1 with ticket)
CREATE TABLE IF NOT EXISTS "ticket_location" (
    ticket_id UUID PRIMARY KEY,
    location geometry(Point, 4326),
    address VARCHAR(255),
    location_method VARCHAR(20),
    CONSTRAINT fk_ticket_location_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ticket_location_geom ON "ticket_location" USING GIST (location);

-- 2. Create ticket_assessment table (System/Admin Data 1-1 with ticket)
CREATE TABLE IF NOT EXISTS "ticket_assessment" (
    ticket_id UUID PRIMARY KEY,
    priority VARCHAR(20),
    priority_reasoning TEXT,
    is_life_threatening BOOLEAN DEFAULT FALSE,
    needs_manual_review BOOLEAN DEFAULT FALSE,
    review_note TEXT,
    deadline TIMESTAMP,
    CONSTRAINT fk_ticket_assessment_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ticket_assessment_priority ON "ticket_assessment"(priority);
CREATE INDEX IF NOT EXISTS idx_ticket_assessment_deadline ON "ticket_assessment"(deadline);

-- 3. Create user_profile table (Info Data 1-1 with user_account, without department_id)
CREATE TABLE IF NOT EXISTS "user_profile" (
    user_id UUID PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES "user_account"(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_profile_role FOREIGN KEY (role_id) REFERENCES "role"(role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_profile_phone_number ON "user_profile"(phone_number);
CREATE INDEX IF NOT EXISTS idx_user_profile_role_id ON "user_profile"(role_id);

-- 4. Create user_address table (Location Data 1-1 with user_account)
CREATE TABLE IF NOT EXISTS "user_address" (
    user_id UUID PRIMARY KEY,
    province VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES "user_account"(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_address_province_ward ON "user_address"(province, ward);
