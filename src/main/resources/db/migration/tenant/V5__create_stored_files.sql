-- Metadata of files whose bytes live in S3. The API never streams file bytes itself: clients upload
-- and download directly with short-lived pre-signed URLs.
CREATE TABLE stored_files (
    id           UUID         PRIMARY KEY,
    object_key   VARCHAR(300) NOT NULL UNIQUE,
    purpose      VARCHAR(30)  NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    event_id     BIGINT       REFERENCES events (id) ON DELETE SET NULL,
    uploaded_by  UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ,

    CONSTRAINT ck_files_status CHECK (status IN ('PENDING', 'READY')),
    CONSTRAINT ck_files_size CHECK (size_bytes > 0)
);

ALTER TABLE events ADD COLUMN poster_file_id UUID REFERENCES stored_files (id) ON DELETE SET NULL;
