-- The employees whose pay we manage. Columns follow docs/requirements/requirements.md.
-- No extra indexes yet on purpose: they are added together with the queries that need them, once measured.
CREATE TABLE employees (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name     TEXT    NOT NULL,
    email         TEXT    NOT NULL UNIQUE,
    country       TEXT    NOT NULL,                                 -- ISO 3166-1 alpha-2, e.g. 'IN'
    department    TEXT    NOT NULL,
    job_title     TEXT    NOT NULL,
    annual_salary INTEGER NOT NULL CHECK (annual_salary > 0),       -- whole units of `currency`, so no rounding errors
    currency      TEXT    NOT NULL,                                 -- ISO 4217, e.g. 'INR'
    hire_date     TEXT    NOT NULL,                                 -- ISO date, 'yyyy-MM-dd'
    status        TEXT    NOT NULL CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED'))
);
