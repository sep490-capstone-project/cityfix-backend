-- Migration V26: Create user_department table for N-N relationship between user_account and department
CREATE TABLE IF NOT EXISTS user_department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    department_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_department_user FOREIGN KEY (user_id) REFERENCES user_account(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_department_dept FOREIGN KEY (department_id) REFERENCES department(department_id) ON DELETE CASCADE,
    CONSTRAINT uq_user_department UNIQUE (user_id, department_id)
);

CREATE INDEX IF NOT EXISTS idx_user_department_user ON user_department(user_id);
CREATE INDEX IF NOT EXISTS idx_user_department_dept ON user_department(department_id);

-- Migrate existing relationships from user_account.department_id
INSERT INTO user_department (id, user_id, department_id)
SELECT gen_random_uuid(), u.user_id, u.department_id
FROM user_account u
WHERE u.department_id IS NOT NULL
ON CONFLICT (user_id, department_id) DO NOTHING;

-- Migrate existing relationships from department.manager_id
INSERT INTO user_department (id, user_id, department_id)
SELECT gen_random_uuid(), d.manager_id, d.department_id
FROM department d
WHERE d.manager_id IS NOT NULL
ON CONFLICT (user_id, department_id) DO NOTHING;
