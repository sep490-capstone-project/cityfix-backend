-- Normalize legacy TicketStatus values
UPDATE ticket SET status = 'ASSIGNED' WHERE status = 'OPEN';
UPDATE ticket_history SET previous_status = 'ASSIGNED' WHERE previous_status = 'OPEN';
UPDATE ticket_history SET current_status = 'ASSIGNED' WHERE current_status = 'OPEN';

-- Normalize legacy ActionType values
UPDATE ticket_history SET action_type = 'ROUTED' WHERE action_type = 'AI_REVIEWED';
UPDATE ticket_history SET action_type = 'ASSIGNED' WHERE action_type = 'REASSIGNED';

-- Normalize legacy AssignmentStatus values on ticket_step
UPDATE ticket_step
SET assignment_status = 'ASSIGNED',
    updated_at = NOW()
WHERE assignment_status IN ('OPEN', 'PENDING_PREVIOUS', 'TRANSFERRED');

ALTER TABLE ticket_step ALTER COLUMN assignment_status SET DEFAULT 'ASSIGNED';
