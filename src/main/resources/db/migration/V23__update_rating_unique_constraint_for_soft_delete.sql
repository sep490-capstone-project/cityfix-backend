-- V23__update_rating_unique_constraint_for_soft_delete.sql
-- Thay thế UNIQUE constraint cũ bằng Partial Unique Index cho các bản ghi chưa bị xóa (is_deleted = false)

ALTER TABLE "rating" DROP CONSTRAINT IF EXISTS uq_rating_user_ticket;

DROP INDEX IF EXISTS uq_rating_user_ticket_active;
CREATE UNIQUE INDEX uq_rating_user_ticket_active ON "rating"(user_id, ticket_id) WHERE is_deleted = false;
