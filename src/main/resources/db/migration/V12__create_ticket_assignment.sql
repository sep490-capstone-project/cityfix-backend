CREATE TABLE ticket_assignment (
    assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    department_id UUID NOT NULL,
    assignee_id UUID,
    assignment_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignment_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id),
    CONSTRAINT fk_assignment_department FOREIGN KEY (department_id) REFERENCES "department"(department_id),
    CONSTRAINT fk_assignment_assignee FOREIGN KEY (assignee_id) REFERENCES "user_account"(user_id)
);

ALTER TABLE ticket DROP COLUMN IF EXISTS department_id;
ALTER TABLE ticket DROP COLUMN IF EXISTS assignee_id;
