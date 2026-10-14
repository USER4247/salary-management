CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    full_name     TEXT    NOT NULL,
    role          TEXT    NOT NULL CHECK (role IN ('HR_ADMIN', 'HR_VIEWER', 'COUNTRY_HR')),
    country_scope TEXT,
    enabled       INTEGER NOT NULL DEFAULT 1,
    created_at    TEXT    NOT NULL,
    CHECK (role <> 'COUNTRY_HR' OR country_scope IS NOT NULL)
);

-- Only a hash of each refresh token is stored. Tokens rotate: a used token is kept (used_at)
-- so that presenting it again can be recognised as reuse and the whole family revoked.
CREATE TABLE refresh_tokens (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users (id),
    family_id   TEXT    NOT NULL,
    token_hash  TEXT    NOT NULL UNIQUE,
    expires_at  TEXT    NOT NULL,
    used_at     TEXT,
    revoked_at  TEXT,
    created_at  TEXT    NOT NULL
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
