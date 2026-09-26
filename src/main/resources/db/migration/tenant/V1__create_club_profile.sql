-- Runs once inside EVERY club schema (club_<slug>). No schema prefix on purpose:
-- Flyway sets search_path to the club schema before running this.
CREATE TABLE club_profile (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    display_name  VARCHAR(120) NOT NULL,
    description   TEXT,
    contact_email VARCHAR(254),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
