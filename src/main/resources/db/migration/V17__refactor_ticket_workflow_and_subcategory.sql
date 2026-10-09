-- V17: Refactor Ticket Workflow and SubCategory

-- 1. Add is_anonymous column to ticket table
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Drop is_dispatcher column from user_account table
ALTER TABLE user_account DROP COLUMN IF EXISTS is_dispatcher;

-- 3. Create ticket_subcategory join table (N-N)
CREATE TABLE IF NOT EXISTS ticket_subcategory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    sub_category_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_subcategory_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_subcategory_sub_category FOREIGN KEY (sub_category_id) REFERENCES sub_category(sub_category_id) ON DELETE CASCADE,
    CONSTRAINT uk_ticket_subcategory UNIQUE (ticket_id, sub_category_id)
);

-- Migrate sub_category_id data from ticket to ticket_subcategory before dropping sub_category_id
INSERT INTO ticket_subcategory (ticket_id, sub_category_id)
SELECT ticket_id, sub_category_id
FROM ticket
WHERE sub_category_id IS NOT NULL
ON CONFLICT (ticket_id, sub_category_id) DO NOTHING;

-- 4. Create ticket_step table (including notes column)
CREATE TABLE IF NOT EXISTS ticket_step (
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

-- Migrate data from ticket_assignment to ticket_step if ticket_assignment table exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ticket_assignment') THEN
        INSERT INTO ticket_step (ticket_id, step_id, assignee_id, assignment_status, created_at, updated_at)
        SELECT 
            ta.ticket_id,
            COALESCE(
                (SELECT s.step_id FROM step s JOIN ticket t ON t.sub_category_id = s.sub_category_id WHERE t.ticket_id = ta.ticket_id LIMIT 1),
                (SELECT step_id FROM step LIMIT 1)
            ) AS step_id,
            ta.assignee_id,
            ta.assignment_status,
            ta.created_at,
            ta.updated_at
        FROM ticket_assignment ta
        WHERE EXISTS (SELECT 1 FROM step);
    END IF;
END $$;

-- 5. Drop sub_category_id column from ticket table
ALTER TABLE ticket DROP COLUMN IF EXISTS sub_category_id;

-- 6. Drop ticket_assignment table
DROP TABLE IF EXISTS ticket_assignment CASCADE;
