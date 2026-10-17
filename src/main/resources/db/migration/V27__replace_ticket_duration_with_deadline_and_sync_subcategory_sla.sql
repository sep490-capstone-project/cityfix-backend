-- V27__replace_ticket_duration_with_deadline_and_sync_subcategory_sla.sql
-- 1. Thêm cột deadline (TIMESTAMP) trên bảng ticket trước khi xóa cột duration
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS deadline TIMESTAMP;

-- 2. Đồng bộ deadline của các ticket hiện có theo deadline trễ nhất trong ticket_step
--    Fallback sang created_at + duration (hours) nếu chưa có deadline trên ticket_step
UPDATE ticket t
SET deadline = COALESCE(
    (
        SELECT MAX(ts.deadline)
        FROM ticket_step ts
        WHERE ts.ticket_id = t.ticket_id
          AND ts.is_deleted = FALSE
          AND ts.assignment_status NOT IN ('CANCELLED', 'TRANSFERRED')
    ),
    CASE
        WHEN t.duration IS NOT NULL AND t.duration > 0 AND t.created_at IS NOT NULL
        THEN t.created_at + (t.duration * INTERVAL '1 hour')
        ELSE NULL
    END
);

-- 3. Xóa cột duration sau khi đã backfill sang deadline
ALTER TABLE ticket DROP COLUMN IF EXISTS duration;

-- 4. Đồng bộ sla của sub_category bằng tổng duration của các step liên kết
UPDATE sub_category sc
SET sla = COALESCE((
    SELECT SUM(COALESCE(s.duration, 0))
    FROM step s
    WHERE s.sub_category_id = sc.sub_category_id
      AND s.is_deleted = FALSE
), 0);
