ALTER TABLE user_roles
    DROP CONSTRAINT IF EXISTS user_roles_role_name_fkey;

DROP TABLE roles;

ALTER TABLE user_roles
    ADD CONSTRAINT ck_user_roles_role_name
        CHECK (role_name IN ('USER', 'ADMIN'));
