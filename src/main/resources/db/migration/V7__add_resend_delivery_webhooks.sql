ALTER TABLE email_outbox_messages
    ADD COLUMN delivery_status_at TIMESTAMPTZ;

CREATE TABLE resend_webhook_events (
    webhook_id VARCHAR(128) PRIMARY KEY,
    provider_message_id VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    event_created_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resend_webhook_provider_message
    ON resend_webhook_events (provider_message_id, event_created_at DESC)
    WHERE provider_message_id IS NOT NULL;
