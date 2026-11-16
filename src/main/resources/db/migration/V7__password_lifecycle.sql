ALTER TABLE app_user ADD COLUMN password_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN temporary_password_until BIGINT;
