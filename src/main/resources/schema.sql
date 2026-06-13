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

ALTER TABLE IF EXISTS chat_messages
    ADD COLUMN IF NOT EXISTS group_id BIGINT;

ALTER TABLE IF EXISTS chat_messages
    ALTER COLUMN receiver_id DROP NOT NULL;

ALTER TABLE IF EXISTS chat_messages
    ALTER COLUMN group_id DROP NOT NULL;

ALTER TABLE IF EXISTS chat_messages
    ADD COLUMN IF NOT EXISTS sender_deleted_at TIMESTAMP;

ALTER TABLE IF EXISTS chat_messages
    ADD COLUMN IF NOT EXISTS receiver_deleted_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_messages_sender_receiver_client_message_id
    ON chat_messages(sender_id, receiver_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_messages_sender_group_client_message_id
    ON chat_messages(sender_id, group_id, client_message_id);

CREATE INDEX IF NOT EXISTS idx_chat_messages_sender_receiver_media_fingerprint_created_at
    ON chat_messages(sender_id, receiver_id, media_fingerprint, created_at DESC);

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS title TEXT;

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS video_settings TEXT;

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS cover_image_url TEXT;

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS media_fingerprint VARCHAR(64);

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS media_type VARCHAR(40);

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS media_size_bytes BIGINT;

ALTER TABLE IF EXISTS post
    ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(255);

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS title TEXT;

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS video_settings TEXT;

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS cover_image_url TEXT;

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS media_fingerprint VARCHAR(64);

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS media_type VARCHAR(40);

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS media_size_bytes BIGINT;

ALTER TABLE IF EXISTS posts
    ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(255);

CREATE TABLE IF NOT EXISTS web_push_subscription (
    id BIGSERIAL PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    p256dh VARCHAR(512) NOT NULL,
    auth VARCHAR(256) NOT NULL,
    user_agent VARCHAR(512),
    expiration_time BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_web_push_subscription_endpoint
    ON web_push_subscription(endpoint);
