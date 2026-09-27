-- Recruitment lives inside each club schema: one club's drives and applicants are invisible to others.

CREATE TABLE recruitment_drives (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    closes_at   TIMESTAMPTZ,
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_drives_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
);

CREATE TABLE drive_questions (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drive_id   BIGINT       NOT NULL REFERENCES recruitment_drives (id) ON DELETE CASCADE,
    sort_order INT          NOT NULL,
    prompt     VARCHAR(500) NOT NULL,
    required   BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_questions_drive_order UNIQUE (drive_id, sort_order)
);

-- applicant_user_id points ACROSS schemas to public.users: allowed because every club schema
-- lives in the same PostgreSQL database. The DB itself guarantees applicants are real accounts.
CREATE TABLE applications (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    drive_id          BIGINT      NOT NULL REFERENCES recruitment_drives (id) ON DELETE CASCADE,
    applicant_user_id UUID        NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_applications_drive_applicant UNIQUE (drive_id, applicant_user_id),
    CONSTRAINT ck_applications_status CHECK (status IN
        ('APPLIED', 'SHORTLISTED', 'INTERVIEW', 'SELECTED', 'REJECTED', 'WITHDRAWN'))
);

-- the pipeline screen filters one drive by status: composite index matches that query exactly
CREATE INDEX ix_applications_drive_status ON applications (drive_id, status);
CREATE INDEX ix_applications_applicant ON applications (applicant_user_id);

CREATE TABLE application_answers (
    id             BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    question_id    BIGINT NOT NULL REFERENCES drive_questions (id) ON DELETE CASCADE,
    answer         TEXT   NOT NULL,

    CONSTRAINT uk_answers_application_question UNIQUE (application_id, question_id)
);

-- append-only audit trail of every pipeline move (who moved whom, when, why)
CREATE TABLE application_status_changes (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT       NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20)  NOT NULL,
    changed_by     UUID         NOT NULL,
    note           VARCHAR(500),
    changed_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_status_changes_application ON application_status_changes (application_id);
