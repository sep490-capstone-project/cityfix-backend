-- =========================================================================
-- V33: Create ticket_step_dependency table for DAG Workflow Execution
-- =========================================================================

-- 1. Tạo bảng trung gian tự liên kết nhiều-nhiều (Self-referencing Join Table)
CREATE TABLE IF NOT EXISTS "ticket_step_dependency" (
    step_id UUID NOT NULL,
    depends_on_step_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Khóa chính tổ hợp ngăn trùng lặp quan hệ phụ thuộc
    CONSTRAINT pk_ticket_step_dependency PRIMARY KEY (step_id, depends_on_step_id),
    
    -- Khóa ngoại ON DELETE CASCADE trỏ về ticket_step(id)
    CONSTRAINT fk_tsd_step FOREIGN KEY (step_id) 
        REFERENCES "ticket_step"(id) ON DELETE CASCADE,
    CONSTRAINT fk_tsd_depends_on FOREIGN KEY (depends_on_step_id) 
        REFERENCES "ticket_step"(id) ON DELETE CASCADE,
        
    -- Ràng buộc kiểm tra không được tự phụ thuộc vào chính mình
    CONSTRAINT chk_tsd_no_self_dependency CHECK (step_id <> depends_on_step_id)
);

-- 2. Tạo chỉ mục tối ưu hiệu năng truy vấn quan hệ phụ thuộc
CREATE INDEX IF NOT EXISTS idx_tsd_step_id ON "ticket_step_dependency"(step_id);
CREATE INDEX IF NOT EXISTS idx_tsd_depends_on_step_id ON "ticket_step_dependency"(depends_on_step_id);

-- 3. Data Migration: Tự động chuyển đổi các bước của các vé hiện có dựa trên order_index cũ
--    Đảm bảo các vé lịch sử vẫn chạy hợp lệ theo DAG mà không cần can thiệp thủ công
WITH ordered_steps AS (
    SELECT 
        id AS current_step_id,
        ticket_id,
        LAG(id) OVER (PARTITION BY ticket_id ORDER BY order_index ASC, created_at ASC) AS prev_step_id
    FROM "ticket_step"
    WHERE is_deleted = false AND order_index IS NOT NULL
)
INSERT INTO "ticket_step_dependency" (step_id, depends_on_step_id)
SELECT current_step_id, prev_step_id
FROM ordered_steps
WHERE prev_step_id IS NOT NULL
ON CONFLICT (step_id, depends_on_step_id) DO NOTHING;
