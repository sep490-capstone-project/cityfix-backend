-- Migration V34: Drop order_index from ticket_step and decouple manager from department

-- 1. Xóa hoàn toàn cột order_index khỏi bảng ticket_step
ALTER TABLE "ticket_step" DROP COLUMN IF EXISTS "order_index";

-- 2. Tách rời hoàn toàn Manager khỏi phòng ban:
-- Xóa khóa ngoại và cột manager_id trong bảng department
ALTER TABLE "department" DROP CONSTRAINT IF EXISTS "fk_department_manager";
ALTER TABLE "department" DROP COLUMN IF EXISTS "manager_id";

-- Dọn sạch các bản ghi liên kết của tài khoản Manager trong bảng user_department
DELETE FROM "user_department" WHERE user_id IN (
    SELECT u.user_id FROM "user_account" u 
    JOIN "role" r ON u.role_id = r.role_id 
    WHERE r.role_name IN ('MANAGER', 'ROLE_MANAGER')
);
