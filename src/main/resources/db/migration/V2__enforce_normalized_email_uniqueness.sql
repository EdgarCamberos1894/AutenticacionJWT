CREATE UNIQUE INDEX uq_users_email_normalized ON users (LOWER(email));
