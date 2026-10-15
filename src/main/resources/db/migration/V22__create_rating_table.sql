-- V22__create_rating_table.sql
-- Tạo bảng rating cho tính năng đánh giá vé

CREATE TABLE IF NOT EXISTS "rating" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    rate INT NOT NULL,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    feedback TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES "user_account"(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_ticket FOREIGN KEY (ticket_id) REFERENCES "ticket"(ticket_id) ON DELETE CASCADE,
    CONSTRAINT chk_rating_rate CHECK (rate >= 1 AND rate <= 5),
    CONSTRAINT uq_rating_user_ticket UNIQUE (user_id, ticket_id)
);

CREATE INDEX IF NOT EXISTS idx_rating_ticket_id ON "rating"(ticket_id);
CREATE INDEX IF NOT EXISTS idx_rating_user_id ON "rating"(user_id);
