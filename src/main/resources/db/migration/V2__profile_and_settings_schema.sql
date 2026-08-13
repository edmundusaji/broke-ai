-- Profile and Settings foundation for PostgreSQL.
-- Existing users and receipt identifiers remain BIGINT to preserve foreign keys,
-- JWT subjects, and production data. New standalone resources use UUID keys.

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Guest usernames generated before this migration used all 32 UUID hex digits
-- ("guest_" + 32 characters). Re-key only guest usernames to a stable value
-- derived from their database ID; registered usernames must never be rewritten.
UPDATE users
SET username = 'guest_' || lpad(to_hex(id), 24, '0')
WHERE is_guest = TRUE
  AND char_length(username) NOT BETWEEN 3 AND 30;

UPDATE users
SET email = lower(trim(email))
WHERE email IS NOT NULL;

DO $$
BEGIN
    -- Preserve legacy profile data instead of preventing application startup.
    -- If every value already complies, tighten the physical column as well.
    IF NOT EXISTS (SELECT 1 FROM users WHERE char_length(full_name) > 100) THEN
        ALTER TABLE users ALTER COLUMN full_name TYPE VARCHAR(100);
    END IF;
END $$;

ALTER TABLE users
    ALTER COLUMN username TYPE CITEXT USING username::citext,
    ALTER COLUMN email TYPE CITEXT USING email::citext;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'users'
          AND column_name = 'password'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'users'
          AND column_name = 'password_hash'
    ) THEN
        ALTER TABLE users RENAME COLUMN password TO password_hash;
    END IF;
END $$;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS pending_email CITEXT,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS phone_e164 VARCHAR(20),
    ADD COLUMN IF NOT EXISTS avatar_object_key TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS profile_revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT ck_users_full_name_length
        CHECK (char_length(full_name) BETWEEN 1 AND 100) NOT VALID,
    ADD CONSTRAINT ck_users_username_length
        CHECK (char_length(username) BETWEEN 3 AND 30) NOT VALID,
    ADD CONSTRAINT ck_users_phone_e164
        CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('active', 'pending_deletion', 'deleted', 'suspended')),
    ADD CONSTRAINT ck_users_registered_credentials
        CHECK (is_guest OR (email IS NOT NULL AND password_hash IS NOT NULL)) NOT VALID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_pending_email
    ON users (pending_email)
    WHERE pending_email IS NOT NULL;

ALTER TABLE receipt
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS ix_receipt_user_date_active
    ON receipt (user_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    currency_code CHAR(3) NOT NULL DEFAULT 'IDR',
    language_code VARCHAR(20) NOT NULL DEFAULT 'en',
    region_code CHAR(2) NOT NULL DEFAULT 'ID',
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Jakarta',
    theme_mode VARCHAR(10) NOT NULL DEFAULT 'light',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_user_preferences_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_user_preferences_language CHECK (language_code ~ '^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$'),
    CONSTRAINT ck_user_preferences_region CHECK (region_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_user_preferences_theme CHECK (theme_mode IN ('light', 'dark', 'system'))
);

CREATE TABLE notification_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    spending_reminders BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_time TIME,
    weekly_summary BOOLEAN NOT NULL DEFAULT TRUE,
    monthly_report BOOLEAN NOT NULL DEFAULT TRUE,
    security_alerts BOOLEAN NOT NULL DEFAULT TRUE,
    product_updates BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE user_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    device_name VARCHAR(100),
    push_token_encrypted TEXT,
    push_token_hash TEXT,
    app_version VARCHAR(30),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_user_devices_platform CHECK (platform IN ('android', 'ios', 'web', 'windows', 'macos', 'linux'))
);

CREATE UNIQUE INDEX uq_user_devices_push_token_hash
    ON user_devices (push_token_hash)
    WHERE push_token_hash IS NOT NULL;

CREATE TABLE privacy_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    personalized_insights BOOLEAN NOT NULL DEFAULT TRUE,
    anonymous_analytics BOOLEAN NOT NULL DEFAULT TRUE,
    policy_version VARCHAR(30) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    source_device_id UUID REFERENCES user_devices(id) ON DELETE SET NULL,
    source_platform VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE privacy_consent_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    personalized_insights BOOLEAN NOT NULL,
    anonymous_analytics BOOLEAN NOT NULL,
    policy_version VARCHAR(30) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL,
    source_device_id UUID REFERENCES user_devices(id) ON DELETE SET NULL,
    source_platform VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_privacy_consent_history_user_consented
    ON privacy_consent_history (user_id, consented_at DESC);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash TEXT NOT NULL,
    device_id UUID REFERENCES user_devices(id) ON DELETE SET NULL,
    ip_hash TEXT,
    user_agent TEXT,
    last_active_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX ix_user_sessions_user_active
    ON user_sessions (user_id, expires_at DESC)
    WHERE revoked_at IS NULL;

CREATE TABLE email_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    new_email CITEXT NOT NULL,
    verification_token_hash TEXT NOT NULL UNIQUE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX uq_email_change_requests_user_active
    ON email_change_requests (user_id)
    WHERE verified_at IS NULL AND cancelled_at IS NULL;

CREATE TABLE support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'open',
    app_version VARCHAR(30) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    os_version VARCHAR(60) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    locale VARCHAR(35) NOT NULL,
    current_route VARCHAR(255),
    attachment_object_key TEXT,
    diagnostic_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_support_tickets_type CHECK (type IN ('support', 'bug')),
    CONSTRAINT ck_support_tickets_status CHECK (status IN ('open', 'in_progress', 'waiting_for_user', 'resolved', 'closed'))
);

CREATE INDEX ix_support_tickets_user_created
    ON support_tickets (user_id, created_at DESC);

CREATE TABLE data_export_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'queued',
    export_format VARCHAR(20) NOT NULL DEFAULT 'zip_json_csv',
    object_key TEXT,
    idempotency_key VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_data_export_jobs_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_data_export_jobs_status CHECK (status IN ('queued', 'processing', 'ready', 'failed', 'expired')),
    CONSTRAINT ck_data_export_jobs_format CHECK (export_format = 'zip_json_csv')
);

CREATE INDEX ix_data_export_jobs_user_requested
    ON data_export_jobs (user_id, requested_at DESC);

CREATE TABLE account_deletion_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    idempotency_key VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    scheduled_for TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '7 days'),
    cancelled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_account_deletion_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_account_deletion_status CHECK (status IN ('pending', 'cancelled', 'processing', 'completed'))
);

CREATE UNIQUE INDEX uq_account_deletion_user_active
    ON account_deletion_requests (user_id)
    WHERE status IN ('pending', 'processing');

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_records_operation UNIQUE (user_id, operation, idempotency_key)
);

CREATE INDEX ix_idempotency_records_expiry
    ON idempotency_records (expires_at);

CREATE TABLE user_sync_state (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'synced',
    last_synced_at TIMESTAMPTZ,
    server_revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_user_sync_state_status CHECK (status IN ('synced', 'syncing', 'offline', 'failed', 'action_required'))
);

CREATE TABLE security_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(80) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    session_id UUID REFERENCES user_sessions(id) ON DELETE SET NULL,
    ip_hash TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_security_audit_actor CHECK (actor_type IN ('user', 'system', 'support', 'admin'))
);

CREATE INDEX ix_security_audit_user_created
    ON security_audit_log (user_id, created_at DESC);

CREATE TABLE faq_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    locale VARCHAR(35) NOT NULL,
    category VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    publication_status VARCHAR(20) NOT NULL DEFAULT 'draft',
    minimum_app_version VARCHAR(30),
    maximum_app_version VARCHAR(30),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revision BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_faq_publication_status CHECK (publication_status IN ('draft', 'published', 'archived'))
);

CREATE INDEX ix_faq_articles_locale_order_published
    ON faq_articles (locale, category, display_order)
    WHERE publication_status = 'published';

INSERT INTO user_preferences (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO notification_preferences (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_sync_state (user_id, last_synced_at)
SELECT id, now() FROM users
ON CONFLICT (user_id) DO NOTHING;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    target_table TEXT;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'users',
        'receipt',
        'user_preferences',
        'notification_preferences',
        'privacy_preferences',
        'user_sessions',
        'user_devices',
        'email_change_requests',
        'support_tickets',
        'data_export_jobs',
        'account_deletion_requests',
        'user_sync_state',
        'faq_articles'
    ]
    LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS %I ON %I',
            'trg_' || target_table || '_updated_at',
            target_table
        );
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            'trg_' || target_table || '_updated_at',
            target_table
        );
    END LOOP;
END $$;

COMMENT ON TABLE user_devices IS
    'Contains device and push-delivery metadata only. App-lock PINs, biometric data, and local verifiers must never be stored here.';

COMMENT ON COLUMN data_export_jobs.object_key IS
    'Private object-storage key. API responses expose only short-lived signed download URLs.';
