UPDATE email_outbox_messages
SET nonce = NULL,
    ciphertext = NULL
WHERE status = 'CANCELLED'
  AND (nonce IS NOT NULL OR ciphertext IS NOT NULL);
