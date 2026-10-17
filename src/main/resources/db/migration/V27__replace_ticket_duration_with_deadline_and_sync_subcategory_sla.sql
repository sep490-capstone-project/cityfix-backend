-- V27__replace_ticket_duration_with_deadline_and_sync_subcategory_sla.sql
-- 1. Thay thế cột duration bằng deadline (TIMESTAMP) trên bảng ticket
ALTER TABLE ticket DROP COLUMN IF EXISTS duration;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS deadline TIMESTAMP;

-- 2. Đồng bộ deadline của các ticket hiện có theo deadline trễ nhất trong ticket_step
UPDATE ticket t
SET deadline = (
    SELECT MAX(ts.deadline)
    FROM ticket_step ts
    WHERE ts.ticket_id = t.ticket_id
      AND ts.is_deleted = FALSE
      AND ts.assignment_status NOT IN ('CANCELLED', 'TRANSFERRED')
);

-- 3. Đồng bộ sla của sub_category bằng tổng duration của các step liên kết
UPDATE sub_category sc
SET sla = COALESCE((
    SELECT SUM(COALESCE(s.duration, 0))
    FROM step s
    WHERE s.sub_category_id = sc.sub_category_id
      AND s.is_deleted = FALSE
), 0);
