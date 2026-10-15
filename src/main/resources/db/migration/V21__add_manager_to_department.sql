-- Migration V21: Add manager_id to department table referencing user_account
ALTER TABLE department ADD COLUMN IF NOT EXISTS manager_id UUID;

ALTER TABLE department 
ADD CONSTRAINT fk_department_manager 
FOREIGN KEY (manager_id) REFERENCES user_account(user_id) 
ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_department_manager ON department(manager_id);
