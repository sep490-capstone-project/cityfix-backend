-- Enable pgcrypto extension for UUID generation if needed
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------
-- Table: role
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS "roles" (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- -----------------------------------------------------
-- Table: user
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS "users" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    province VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    role_id INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES "roles"(id) ON DELETE RESTRICT
);

-- Index for fast user lookup by email (used in login & JWT authentication)
CREATE INDEX IF NOT EXISTS idx_user_email ON "users"(email);
CREATE INDEX IF NOT EXISTS idx_user_phone ON "users"(phone_number);

-- -----------------------------------------------------
-- Seed Initial Roles
-- -----------------------------------------------------
INSERT INTO "roles" (name) VALUES
('CITIZEN'),
('ADMIN'),
('GOVERNMENT')
ON CONFLICT (name) DO NOTHING;
