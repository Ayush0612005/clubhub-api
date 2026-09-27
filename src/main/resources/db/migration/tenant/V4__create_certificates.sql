-- Certificates are stored as FACTS (who, what, when); the PDF is rendered on demand from this row.
-- The UUID id is the public verification code printed on the certificate: unguessable, unlike a sequence.
CREATE TABLE certificates (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    event_id       BIGINT       REFERENCES events (id) ON DELETE SET NULL,
    title          VARCHAR(150) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    description    VARCHAR(500) NOT NULL,
    issued_by      UUID         NOT NULL,
    issued_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at     TIMESTAMPTZ
);

-- one participation certificate per attendee per event (re-running "issue" is idempotent);
-- partial index: certificates not tied to an event are not constrained
CREATE UNIQUE INDEX uk_certificates_event_user ON certificates (event_id, user_id) WHERE event_id IS NOT NULL;
CREATE INDEX ix_certificates_user ON certificates (user_id);
