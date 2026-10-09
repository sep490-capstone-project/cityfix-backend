-- V17: Refactor Ticket Workflow and SubCategory

-- 1. Add is_anonymous column to ticket table
ALTER TABLE ticket ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Drop is_dispatcher column from user_account table
ALTER TABLE user_account DROP COLUMN IF EXISTS is_dispatcher;

-- 3. Drop sub_category_id column from ticket table
ALTER TABLE ticket DROP COLUMN IF EXISTS sub_category_id;

-- 4. Drop ticket_assignment table
DROP TABLE IF EXISTS ticket_assignment CASCADE;

-- 5. Create ticket_step table (including notes column)
CREATE TABLE ticket_step (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    step_id UUID NOT NULL,
    assignee_id UUID,
    assignment_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    order_index INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    deadline TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_step_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_step_step FOREIGN KEY (step_id) REFERENCES step(step_id),
    CONSTRAINT fk_ticket_step_assignee FOREIGN KEY (assignee_id) REFERENCES user_account(user_id)
);

-- 6. Create ticket_subcategory join table (N-N)
CREATE TABLE ticket_subcategory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    sub_category_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_subcategory_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_subcategory_sub_category FOREIGN KEY (sub_category_id) REFERENCES sub_category(sub_category_id) ON DELETE CASCADE,
    CONSTRAINT uk_ticket_subcategory UNIQUE (ticket_id, sub_category_id)
);
