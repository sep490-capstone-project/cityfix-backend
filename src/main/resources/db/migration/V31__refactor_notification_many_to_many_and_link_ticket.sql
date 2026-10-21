-- Phase 1 (Expand): Create user_notification table and link ticket_id
-- Resilient migration supporting zero-downtime rolling deployment, idempotent re-runs, and dirty data handling

-- 1. Create junction table user_notification (if not exists)
CREATE TABLE IF NOT EXISTS user_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL,
    user_id UUID NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Add foreign keys safely if not exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'user_notification_notification_id_fkey' 
          AND table_name = 'user_notification'
    ) THEN
        ALTER TABLE user_notification 
            ADD CONSTRAINT user_notification_notification_id_fkey 
            FOREIGN KEY (notification_id) REFERENCES notifications(notification_id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'user_notification_user_id_fkey' 
          AND table_name = 'user_notification'
    ) THEN
        ALTER TABLE user_notification 
            ADD CONSTRAINT user_notification_user_id_fkey 
            FOREIGN KEY (user_id) REFERENCES user_account(user_id) ON DELETE CASCADE;
    END IF;
END $$;

-- 3. Add ticket_id to notifications safely
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS ticket_id UUID REFERENCES ticket(ticket_id) ON DELETE CASCADE;

-- 4. Migrate existing data from notifications safely:
-- - Only migrate rows where user_id actually exists in user_account (avoid FK constraint failure)
-- - Deduplicate by (user_id, notification_id) using DISTINCT ON
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'notifications' 
          AND column_name = 'user_id'
    ) THEN
        -- Make legacy columns nullable to support zero-downtime deploy (new app versions won't provide user_id)
        ALTER TABLE notifications ALTER COLUMN user_id DROP NOT NULL;

        -- Copy data only for valid users, avoiding duplicates
        INSERT INTO user_notification (id, notification_id, user_id, is_read, created_at)
        SELECT DISTINCT ON (n.user_id, n.notification_id)
            gen_random_uuid(),
            n.notification_id,
            n.user_id,
            COALESCE(n.is_read, FALSE),
            n.created_at
        FROM notifications n
        INNER JOIN user_account u ON n.user_id = u.user_id
        WHERE n.user_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM user_notification un 
              WHERE un.user_id = n.user_id AND un.notification_id = n.notification_id
          );
    END IF;
END $$;

-- 5. Deduplicate user_notification in case duplicates already existed before adding constraint
DELETE FROM user_notification a
WHERE a.id NOT IN (
    SELECT MIN(b.id::text)::uuid
    FROM user_notification b
    GROUP BY b.user_id, b.notification_id
);

-- 6. Add UNIQUE constraint safely if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'uq_user_notification_user_notif' 
          AND table_name = 'user_notification'
    ) THEN
        ALTER TABLE user_notification 
            ADD CONSTRAINT uq_user_notification_user_notif UNIQUE (user_id, notification_id);
    END IF;
END $$;

-- 7. Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_notifications_ticket_id ON notifications (ticket_id);
CREATE INDEX IF NOT EXISTS idx_user_notification_user_is_read ON user_notification (user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_notification_notif_id ON user_notification (notification_id);
