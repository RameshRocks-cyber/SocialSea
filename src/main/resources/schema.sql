ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS private_account BOOLEAN DEFAULT FALSE;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS resume_json TEXT;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS resume_updated_at TIMESTAMP;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(16) DEFAULT 'en';

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS notification_voice VARCHAR(16) DEFAULT 'male';

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS long_videos_enabled BOOLEAN DEFAULT FALSE;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS cover_photo TEXT;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS vault_lock_json TEXT;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS vault_lock_updated_at TIMESTAMP;

ALTER TABLE IF EXISTS chat_messages
    ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(120);

ALTER TABLE IF EXISTS chat_messages
    ADD COLUMN IF NOT EXISTS media_size_bytes BIGINT;

ALTER TABLE IF EXISTS chat_messages
    ADD COLUMN IF NOT EXISTS media_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_messages_sender_receiver_client_message_id
    ON chat_messages(sender_id, receiver_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_sender_receiver_media_fingerprint_created_at
    ON chat_messages(sender_id, receiver_id, media_fingerprint, created_at DESC);
