CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(50),
    last_name VARCHAR(50),
    photo VARCHAR(255),
    user_id BIGINT UNIQUE,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)
);