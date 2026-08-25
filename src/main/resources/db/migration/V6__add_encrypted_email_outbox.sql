CREATE TABLE email_outbox_messages (
    id UUID PRIMARY KEY,
    purpose VARCHAR(32) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    nonce BYTEA NOT NULL,
    ciphertext BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL,
    delivery_status VARCHAR(24) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(128),
    provider_message_id VARCHAR(128),
    last_error_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_email_outbox_claim
    ON email_outbox_messages (status, next_attempt_at, created_at);

CREATE INDEX idx_email_outbox_lease
    ON email_outbox_messages (status, locked_at)
    WHERE status = 'PROCESSING';

CREATE UNIQUE INDEX uq_email_outbox_provider_message
    ON email_outbox_messages (provider_message_id)
    WHERE provider_message_id IS NOT NULL;
