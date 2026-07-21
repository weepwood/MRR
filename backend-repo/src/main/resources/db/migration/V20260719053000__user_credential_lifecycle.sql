ALTER TABLE mr_auth_user
    ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS password_version integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS password_changed_at timestamp,
    ADD COLUMN IF NOT EXISTS temporary_password_expires_at timestamp,
    ADD COLUMN IF NOT EXISTS created_by bigint,
    ADD COLUMN IF NOT EXISTS password_reset_at timestamp,
    ADD COLUMN IF NOT EXISTS password_reset_by bigint;

UPDATE mr_auth_user
SET must_change_password = false
WHERE must_change_password IS NULL;

UPDATE mr_auth_user
SET password_version = 1
WHERE password_version IS NULL OR password_version < 1;

CREATE INDEX IF NOT EXISTS idx_mr_auth_user_password_state
    ON mr_auth_user (must_change_password, temporary_password_expires_at);
