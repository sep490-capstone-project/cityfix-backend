-- Migration V34: Drop order_index from ticket_step and decouple manager from department

-- 1. Xóa hoàn toàn cột order_index khỏi bảng ticket_step
ALTER TABLE "ticket_step" DROP COLUMN IF EXISTS "order_index";

-- 2. Tách rời hoàn toàn Manager khỏi bảng department:
-- Xóa khóa ngoại và cột manager_id trong bảng department (Manager không còn là trưởng phòng cố định gắn cứng vào 1 department)
ALTER TABLE "department" DROP CONSTRAINT IF EXISTS "fk_department_manager";
ALTER TABLE "department" DROP COLUMN IF EXISTS "manager_id";

-- GHI CHÚ (Rà soát Data Scope):
-- Giữ nguyên bảng trung gian "user_department" cho tài khoản Manager.
-- Các API xem danh sách vé của Manager (GET /api/v1/manager/tickets, GET /api/v1/manager/tickets/department)
-- vẫn phụ thuộc vào user_department để xác định các phòng ban mà Manager phụ trách.
-- Do đó không xóa các bản ghi trong user_department để tránh làm Manager mất dữ liệu vé.

