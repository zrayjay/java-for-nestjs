ALTER TABLE sec_sessions
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_sec_session_auth_version CHECK (auth_version >= 0);
ALTER TABLE sec_users
    ADD CONSTRAINT ck_sec_user_auth_version CHECK (auth_version >= 0);
