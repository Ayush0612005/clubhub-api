-- Global accounts: one per student, shared across every club they join.
-- Table is "users", not "user": USER is a reserved word in PostgreSQL.
CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    platform_role VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_platform_role CHECK (platform_role IN ('USER', 'PLATFORM_ADMIN'))
);

-- Case-insensitive uniqueness: "Ayush@srmist.edu.in" and "ayush@srmist.edu.in" are the same account.
-- A functional (expression) index enforces it in the DB and also serves lower(email) lookups.
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
