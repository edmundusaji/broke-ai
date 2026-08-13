CREATE TABLE avatar_uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(30) NOT NULL,
    maximum_size_bytes BIGINT NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_avatar_upload_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_avatar_upload_size
        CHECK (maximum_size_bytes BETWEEN 1 AND 5242880)
);

CREATE INDEX ix_avatar_uploads_expiry
    ON avatar_uploads (expires_at)
    WHERE completed_at IS NULL;

CREATE TABLE transaction_clear_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(255) NOT NULL,
    deleted_count BIGINT NOT NULL,
    recoverable_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_transaction_clear_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE TABLE outbound_email_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type VARCHAR(40) NOT NULL,
    recipient_email CITEXT NOT NULL,
    encrypted_payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'queued',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_outbound_email_status CHECK (status IN ('queued', 'processing', 'sent', 'failed'))
);

CREATE INDEX ix_outbound_email_jobs_delivery
    ON outbound_email_jobs (status, next_attempt_at)
    WHERE status IN ('queued', 'failed');

CREATE UNIQUE INDEX uq_user_sessions_access_token_hash
    ON user_sessions (refresh_token_hash);
