-- V18__add_manager_role.sql
-- Add MANAGER role

INSERT INTO "role" (role_name)
SELECT 'MANAGER'
WHERE NOT EXISTS (
    SELECT 1 FROM "role" WHERE role_name = 'MANAGER'
);
