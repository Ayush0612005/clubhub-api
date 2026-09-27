-- Events of one club. PUBLIC events are open to every student; MEMBERS events only to club members.

CREATE TABLE events (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(150) NOT NULL,
    description TEXT,
    venue       VARCHAR(200) NOT NULL,
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ  NOT NULL,
    capacity    INT,
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_events_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_events_capacity CHECK (capacity IS NULL OR capacity > 0),
    CONSTRAINT ck_events_visibility CHECK (visibility IN ('PUBLIC', 'MEMBERS')),
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'))
);

-- "upcoming events" is the main listing: ordered by start time
CREATE INDEX ix_events_starts_at ON events (starts_at);

CREATE TABLE event_registrations (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_registrations_event_user UNIQUE (event_id, user_id)
);

CREATE INDEX ix_registrations_user ON event_registrations (user_id);

-- one row per person who actually showed up; the unique key makes double check-in impossible
CREATE TABLE event_attendance (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id      BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    method        VARCHAR(10) NOT NULL,
    checked_in_by UUID        NOT NULL,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_attendance_event_user UNIQUE (event_id, user_id),
    CONSTRAINT ck_attendance_method CHECK (method IN ('QR', 'MANUAL'))
);

CREATE INDEX ix_attendance_user ON event_attendance (user_id);
