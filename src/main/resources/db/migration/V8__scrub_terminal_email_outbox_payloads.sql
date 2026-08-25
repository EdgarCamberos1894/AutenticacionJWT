ALTER TABLE email_outbox_messages
    ALTER COLUMN nonce DROP NOT NULL,
    ALTER COLUMN ciphertext DROP NOT NULL;

UPDATE email_outbox_messages
SET nonce = NULL,
    ciphertext = NULL
WHERE status IN ('SENT', 'DEAD');
