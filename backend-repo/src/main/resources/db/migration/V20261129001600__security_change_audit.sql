-- Narrow transactional ledger for successful account and role mutations.
-- No credentials, request payloads, usernames, IP addresses or patient data.
CREATE TABLE app.mr_security_change_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type varchar(40) NOT NULL CHECK (event_type IN (
        'USER_REGISTRATION_SUBMITTED', 'USER_REGISTRATION_APPROVED',
        'USER_REGISTRATION_REJECTED', 'USER_REGISTRATION_REOPENED',
        'USER_CREATED', 'USER_PASSWORD_RESET', 'USER_FIRST_PASSWORD_CHANGED',
        'USER_ROLE_OR_STATUS_CHANGED', 'USER_DISABLED', 'USER_PASSWORD_CHANGED',
        'ROLE_UPDATED'
    )),
    actor_user_id bigint CHECK (actor_user_id > 0),
    target_user_id bigint CHECK (target_user_id > 0),
    target_role_code text,
    permissions_edited boolean,
    observed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK ((event_type = 'USER_REGISTRATION_SUBMITTED' AND actor_user_id IS NULL)
        OR (event_type <> 'USER_REGISTRATION_SUBMITTED' AND actor_user_id IS NOT NULL)),
    CHECK ((event_type = 'ROLE_UPDATED' AND target_user_id IS NULL
        AND target_role_code IS NOT NULL AND length(target_role_code) BETWEEN 1 AND 128
        AND permissions_edited IS NOT NULL)
        OR (event_type <> 'ROLE_UPDATED' AND target_user_id IS NOT NULL
        AND target_role_code IS NULL AND permissions_edited IS NULL))
);
CREATE INDEX mr_security_change_audit_observed_idx ON app.mr_security_change_audit(observed_at);
COMMENT ON TABLE app.mr_security_change_audit IS 'Committed account and role mutation facts; written within the business transaction. Not a general request log.';
