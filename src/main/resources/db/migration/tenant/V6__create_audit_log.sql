-- Who did what in this club. Append-only: the application never updates or deletes rows.
CREATE TABLE audit_log (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    UUID        NOT NULL,
    action      VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id   VARCHAR(64) NOT NULL,
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_occurred ON audit_log (occurred_at DESC);
CREATE INDEX ix_audit_target ON audit_log (target_type, target_id);
