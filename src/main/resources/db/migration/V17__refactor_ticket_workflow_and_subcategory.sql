-- V17: Refactor Ticket Workflow and SubCategory

-- Step 1. Add is_anonymous column to ticket table
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

-- Step 2. Create ticket_subcategory join table (N-N)
CREATE TABLE IF NOT EXISTS ticket_subcategory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    sub_category_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ticket_subcategory_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(ticket_id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_subcategory_sub_category FOREIGN KEY (sub_category_id) REFERENCES sub_category(sub_category_id) ON DELETE CASCADE,
    CONSTRAINT uk_ticket_subcategory UNIQUE (ticket_id, sub_category_id)
);

-- Step 3. Migrate sub_category_id data from ticket to ticket_subcategory before dropping sub_category_id
INSERT INTO ticket_subcategory (ticket_id, sub_category_id)
SELECT ticket_id, sub_category_id
FROM ticket
WHERE sub_category_id IS NOT NULL
ON CONFLICT (ticket_id, sub_category_id) DO NOTHING;

-- Step 4. Create ticket_step table (including notes column)
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

-- Step 5. Migrate data from ticket_assignment to ticket_step if ticket_assignment table exists
DO $$
DECLARE
    rec RECORD;
    resolved_step_id UUID;
    resolved_assignee_id UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ticket_assignment') THEN
        FOR rec IN
            SELECT 
                ta.assignment_id,
                ta.ticket_id,
                ta.department_id,
                ta.assignee_id,
                ta.assignment_status,
                ta.created_at,
                ta.updated_at
            FROM ticket_assignment ta
        LOOP
            -- 5.1 Resolve step directly related to the ticket's subcategory
            SELECT s.step_id INTO resolved_step_id
            FROM step s
            JOIN ticket_subcategory tsc ON tsc.sub_category_id = s.sub_category_id
            WHERE tsc.ticket_id = rec.ticket_id
              AND s.is_deleted = FALSE
            ORDER BY s.created_at ASC
            LIMIT 1;

            -- 5.2 Handle active vs historical legacy assignments
            IF rec.assignment_status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') THEN
                -- Active legacy assignment: must have matching step
                IF resolved_step_id IS NULL THEN
                    RAISE NOTICE 'Migration warning: Cannot resolve valid workflow step for active ticket assignment % (ticket %), skipping', rec.assignment_id, rec.ticket_id;
                    CONTINUE;
                END IF;

                -- Resolve representative STAFF for department if assignee_id is null
                IF rec.assignee_id IS NOT NULL THEN
                    -- Verify existing assignee is valid active staff of that department
                    SELECT u.user_id INTO resolved_assignee_id
                    FROM user_account u
                    JOIN role r ON u.role_id = r.role_id
                    WHERE u.user_id = rec.assignee_id
                      AND u.department_id = rec.department_id
                      AND UPPER(r.role_name) = 'STAFF'
                      AND u.status = 'ACTIVE'
                      AND u.is_deleted = FALSE;

                    IF resolved_assignee_id IS NULL THEN
                        -- Fallback to the single representative STAFF of that department
                        SELECT u.user_id INTO resolved_assignee_id
                        FROM user_account u
                        JOIN role r ON u.role_id = r.role_id
                        WHERE u.department_id = rec.department_id
                          AND UPPER(r.role_name) = 'STAFF'
                          AND u.status = 'ACTIVE'
                          AND u.is_deleted = FALSE;
                    END IF;
                ELSE
                    SELECT u.user_id INTO resolved_assignee_id
                    FROM user_account u
                    JOIN role r ON u.role_id = r.role_id
                    WHERE u.department_id = rec.department_id
                      AND UPPER(r.role_name) = 'STAFF'
                      AND u.status = 'ACTIVE'
                      AND u.is_deleted = FALSE;
                END IF;

                IF resolved_assignee_id IS NULL THEN
                    RAISE NOTICE 'Migration warning: No active representative STAFF found for department % in active ticket assignment %, skipping', rec.department_id, rec.assignment_id;
                    CONTINUE;
                END IF;
            ELSE
                -- Historical/terminal assignment: preserve original assignee if user exists
                resolved_assignee_id := NULL;
                IF rec.assignee_id IS NOT NULL THEN
                    SELECT u.user_id INTO resolved_assignee_id
                    FROM user_account u
                    WHERE u.user_id = rec.assignee_id;
                END IF;
            END IF;

            -- Insert into ticket_step if a step was resolved
            IF resolved_step_id IS NOT NULL THEN
                INSERT INTO ticket_step (
                    ticket_id,
                    step_id,
                    assignee_id,
                    assignment_status,
                    order_index,
                    created_at,
                    updated_at
                ) VALUES (
                    rec.ticket_id,
                    resolved_step_id,
                    resolved_assignee_id,
                    rec.assignment_status,
                    0,
                    rec.created_at,
                    rec.updated_at
                );
            END IF;
        END LOOP;
    END IF;
END $$;

-- Step 6. Migration validation assertions
DO $$
BEGIN
    -- Verify no active ticket_step has invalid or null assignee
    IF EXISTS (
        SELECT 1
        FROM ticket_step ts
        LEFT JOIN user_account u ON u.user_id = ts.assignee_id
        LEFT JOIN role r ON r.role_id = u.role_id
        WHERE ts.assignment_status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS')
          AND (
              ts.assignee_id IS NULL
              OR u.is_deleted = TRUE
              OR u.status <> 'ACTIVE'
              OR UPPER(r.role_name) <> 'STAFF'
          )
    ) THEN
        RAISE NOTICE 'Migration warning: Some active ticket steps have invalid or null assignees';
    END IF;

    -- Verify all tickets with old sub_category_id have rows in ticket_subcategory
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = 'sub_category_id'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM ticket t
            WHERE t.sub_category_id IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM ticket_subcategory ts 
                  WHERE ts.ticket_id = t.ticket_id AND ts.sub_category_id = t.sub_category_id
              )
        ) THEN
            RAISE EXCEPTION 'Migration validation failed: subcategories not migrated to ticket_subcategory';
        END IF;
    END IF;
END $$;

-- Step 7. Clean up legacy columns and tables only after successful validation
ALTER TABLE ticket DROP COLUMN IF EXISTS sub_category_id;
-- Keeping ticket_assignment and is_dispatcher as backup for production deployment safety
-- DROP TABLE IF EXISTS ticket_assignment CASCADE;
-- ALTER TABLE user_account DROP COLUMN IF EXISTS is_dispatcher;
