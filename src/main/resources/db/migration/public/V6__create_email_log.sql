-- One row per email sent for a domain event. The unique key makes a redelivered Kafka record
-- (at-least-once delivery) a no-op instead of a second email.
CREATE TABLE email_log (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_event_id UUID         NOT NULL,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject         VARCHAR(200) NOT NULL,
    sent_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_email_log_event_user UNIQUE (source_event_id, user_id)
);
