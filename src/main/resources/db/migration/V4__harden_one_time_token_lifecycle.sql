ALTER TABLE one_time_tokens
    ADD COLUMN invalidated_at TIMESTAMPTZ;

WITH ranked_active_tokens AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, purpose
               ORDER BY created_at DESC, id DESC
           ) AS row_number
    FROM one_time_tokens
    WHERE consumed_at IS NULL
      AND invalidated_at IS NULL
)
UPDATE one_time_tokens
SET invalidated_at = CURRENT_TIMESTAMP
WHERE id IN (
    SELECT id
    FROM ranked_active_tokens
    WHERE row_number > 1
);

CREATE UNIQUE INDEX uq_one_time_tokens_active_user_purpose
    ON one_time_tokens (user_id, purpose)
    WHERE consumed_at IS NULL
      AND invalidated_at IS NULL;
