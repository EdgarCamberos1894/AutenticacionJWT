CREATE TABLE IF NOT EXISTS tokens_blacklist (
    id BIGINT PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
    token VARCHAR(255) NOT NULL,
    expiration_date TIMESTAMP NOT NULL
);