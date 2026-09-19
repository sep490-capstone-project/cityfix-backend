-- V8__recreate_11_tables_with_role.sql

-- 1. Drop existing tables if they exist
DROP TABLE IF EXISTS "auth_sessions" CASCADE;
DROP TABLE IF EXISTS "ai_analysis" CASCADE;
DROP TABLE IF EXISTS "ticket_votes" CASCADE;
DROP TABLE IF EXISTS "report_images" CASCADE;
DROP TABLE IF EXISTS "tickets" CASCADE;
DROP TABLE IF EXISTS "categories" CASCADE;
DROP TABLE IF EXISTS "users" CASCADE;
DROP TABLE IF EXISTS "roles" CASCADE;

-- 2. Create tables according to new 11-table schema

CREATE TABLE "role" (
    role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(50) NOT NULL
);

CREATE TABLE "department" (
    department_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    level VARCHAR(50) NOT NULL,
    contact_email VARCHAR(150),
    phone_number VARCHAR(20)
);

CREATE TABLE "category" (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE "user_account" (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    department_id UUID,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    province VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    address VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_dispatcher BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES "role"(role_id),
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES "department"(department_id)
);

CREATE TABLE "routing_rule" (
    rule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL,
    category_id UUID,
    province VARCHAR(255),
    ward VARCHAR(255),
    CONSTRAINT fk_rule_department FOREIGN KEY (department_id) REFERENCES "department"(department_id),
    CONSTRAINT fk_rule_category FOREIGN KEY (category_id) REFERENCES "category"(category_id)
);

CREATE TABLE "ticket" (
    ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    category_id UUID,
    department_id UUID,
    assignee_id UUID,
    description TEXT NOT NULL,
    severity VARCHAR(20),
    danger_level VARCHAR(20),
    location_method VARCHAR(20),
    location geometry(Point, 4326),
    address VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    is_life_threatening BOOLEAN DEFAULT FALSE,
    needs_manual_review BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_user FOREIGN KEY (user_id) REFERENCES "user_account"(user_id),
    CONSTRAINT fk_ticket_category FOREIGN KEY (category_id) REFERENCES "category"(category_id),
    CONSTRAINT fk_ticket_department FOREIGN KEY (department_id) REFERENCES "department"(department_id),
    CONSTRAINT fk_ticket_assignee FOREIGN KEY (assignee_id) REFERENCES "user_account"(user_id)
);
CREATE INDEX idx_ticket_location ON "ticket" USING GIST (location);

CREATE TABLE "report_image" (
    media_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ri_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE
);

CREATE TABLE "ai_analysis" (
    analysis_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    category_id UUID,
    severity VARCHAR(20),
    danger_level VARCHAR(20),
    confidence_score DECIMAL(5,2),
    description TEXT,
    is_accepted_by_human BOOLEAN,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_category FOREIGN KEY (category_id) REFERENCES "category"(category_id) ON DELETE SET NULL
);

CREATE TABLE "ticket_vote" (
    vote_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tv_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_tv_user FOREIGN KEY (user_id) REFERENCES "user_account"(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_ticket_user_vote UNIQUE (ticket_id, user_id)
);

CREATE TABLE "ticket_history" (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    actor_id UUID,
    action_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(20),
    current_status VARCHAR(20),
    action_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_th_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_th_actor FOREIGN KEY (actor_id) REFERENCES "user_account"(user_id)
);

CREATE TABLE "resolution_evidence" (
    evidence_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    resolution_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_re_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_re_staff FOREIGN KEY (staff_id) REFERENCES "user_account"(user_id)
);

-- Seed initial roles
INSERT INTO "role" (role_name) VALUES ('RESIDENT'), ('STAFF'), ('ADMIN');
