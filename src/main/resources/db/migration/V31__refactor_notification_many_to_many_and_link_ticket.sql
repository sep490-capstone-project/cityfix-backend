-- Phase 1 (Expand): Create user_notification table and link ticket_id
-- Keeps legacy columns user_id and is_read nullable for zero-downtime rolling deployment

-- 1. Create junction table user_notification
CREATE TABLE IF NOT EXISTS user_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notifications(notification_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_notification_user_notif UNIQUE (user_id, notification_id)
);

-- 2. Add ticket_id to notifications (nullable to allow general notifications, cascade on ticket deletion)
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS ticket_id UUID REFERENCES ticket(ticket_id) ON DELETE CASCADE;

-- 3. Migrate existing data from notifications to user_notification if user_id column exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'notifications' 
          AND column_name = 'user_id'
    ) THEN
        INSERT INTO user_notification (id, notification_id, user_id, is_read, created_at)
        SELECT gen_random_uuid(), notification_id, user_id, COALESCE(is_read, FALSE), created_at
        FROM notifications
        WHERE user_id IS NOT NULL
        ON CONFLICT DO NOTHING;

        -- Make legacy columns nullable to support zero-downtime deploy (new app versions won't provide user_id)
        ALTER TABLE notifications ALTER COLUMN user_id DROP NOT NULL;
    END IF;
END $$;

-- 4. Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_notifications_ticket_id ON notifications (ticket_id);
CREATE INDEX IF NOT EXISTS idx_user_notification_user_is_read ON user_notification (user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_notification_notif_id ON user_notification (notification_id);
