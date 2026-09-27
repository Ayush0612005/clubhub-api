-- A user's inbox spans all their clubs, so notifications live in public (not in a club schema).
CREATE TABLE notifications (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tenant_id       UUID          REFERENCES tenants (id) ON DELETE CASCADE,
    source_event_id UUID          NOT NULL,
    type            VARCHAR(40)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    link            VARCHAR(300),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    read_at         TIMESTAMPTZ,

    -- Kafka delivers at-least-once: a redelivered event must not notify anyone twice
    CONSTRAINT uk_notifications_event_user UNIQUE (source_event_id, user_id)
);

-- inbox query: newest first for one user
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
-- unread badge: small partial index over unread rows only
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
