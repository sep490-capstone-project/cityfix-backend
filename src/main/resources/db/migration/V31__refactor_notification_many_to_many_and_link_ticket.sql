-- 1. Create junction table user_notification
CREATE TABLE user_notification (
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
    ADD COLUMN ticket_id UUID REFERENCES ticket(ticket_id) ON DELETE CASCADE;

-- 3. Migrate existing data from notifications to user_notification
INSERT INTO user_notification (id, notification_id, user_id, is_read, created_at)
SELECT gen_random_uuid(), notification_id, user_id, COALESCE(is_read, FALSE), created_at
FROM notifications
ON CONFLICT DO NOTHING;

-- 4. Drop user_id and is_read from notifications
ALTER TABLE notifications DROP COLUMN user_id;
ALTER TABLE notifications DROP COLUMN is_read;

-- 5. Create indexes for performance
CREATE INDEX idx_notifications_ticket_id ON notifications (ticket_id);
CREATE INDEX idx_user_notification_user_is_read ON user_notification (user_id, is_read, created_at DESC);
CREATE INDEX idx_user_notification_notif_id ON user_notification (notification_id);
