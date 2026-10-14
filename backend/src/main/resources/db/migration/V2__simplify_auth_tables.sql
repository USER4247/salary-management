-- Simplifies the auth tables (see ADR-0004):
--  * roles become SUPERADMIN, HR_MANAGER (was HR_ADMIN), HR_VIEWER, COUNTRY_HR
--  * refresh tokens no longer track families or "used" state: a used token is simply deleted
-- SQLite cannot change a CHECK constraint in place, so the users table is rebuilt.

DROP TABLE refresh_tokens;

CREATE TABLE users_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT      NOT NULL UNIQUE,
    password_hash TEXT      NOT NULL,
    full_name     TEXT      NOT NULL,
    role          TEXT      NOT NULL CHECK (role IN ('SUPERADMIN', 'HR_MANAGER', 'HR_VIEWER', 'COUNTRY_HR')),
    country_scope TEXT,
    enabled       INTEGER   NOT NULL DEFAULT 1,
    created_at    TIMESTAMP NOT NULL,
    CHECK (role <> 'COUNTRY_HR' OR country_scope IS NOT NULL)
);

INSERT INTO users_new (id, email, password_hash, full_name, role, country_scope, enabled, created_at)
SELECT id, email, password_hash, full_name,
       CASE role WHEN 'HR_ADMIN' THEN 'HR_MANAGER' ELSE role END,
       country_scope, enabled, created_at
FROM users;

DROP TABLE users;
ALTER TABLE users_new RENAME TO users;

CREATE TABLE refresh_tokens (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER   NOT NULL REFERENCES users (id),
    token_hash TEXT      NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
