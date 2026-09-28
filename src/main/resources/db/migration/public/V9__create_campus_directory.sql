-- Campus directory: every club at SRM KTR, whether or not it runs a ClubHub workspace.
-- A listing is "unclaimed" until a club takes it over (tenant_id set), like a business page on a map.
CREATE TABLE club_listings (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug         VARCHAR(60)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    category     VARCHAR(40)  NOT NULL,
    kind         VARCHAR(40)  NOT NULL,
    home         VARCHAR(120),
    description  VARCHAR(500),
    official_url VARCHAR(500),
    source_url   VARCHAR(500),
    tenant_id    UUID REFERENCES tenants (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_club_listings_slug UNIQUE (slug)
);

-- Events from any source: SRM's official feed, the platform team, or student suggestions.
-- Only APPROVED rows are ever shown to students; PENDING is the moderation queue.
CREATE TABLE campus_events (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    club_listing_id  UUID REFERENCES club_listings (id) ON DELETE SET NULL,
    title            VARCHAR(200) NOT NULL,
    description      VARCHAR(2000),
    starts_at        TIMESTAMPTZ,
    ends_at          TIMESTAMPTZ,
    venue            VARCHAR(200),
    registration_url VARCHAR(500),
    source_url       VARCHAR(500),
    source           VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    external_id      VARCHAR(300),
    submitted_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMPTZ,

    CONSTRAINT ck_campus_events_source CHECK (source IN ('SRM_FEED', 'ADMIN', 'STUDENT')),
    CONSTRAINT ck_campus_events_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- a published event must have a date; the queue may hold undated imports for the admin to fix
    CONSTRAINT ck_campus_events_dated CHECK (status <> 'APPROVED' OR starts_at IS NOT NULL),
    -- re-running the importer never duplicates a feed item
    CONSTRAINT uk_campus_events_external UNIQUE (external_id)
);

CREATE INDEX ix_campus_events_feed ON campus_events (status, starts_at);
CREATE INDEX ix_campus_events_club ON campus_events (club_listing_id);

-- "Club X is recruiting for Y": always links out to the club's own application form.
CREATE TABLE campus_recruitments (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    club_listing_id UUID         NOT NULL REFERENCES club_listings (id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(2000),
    apply_url       VARCHAR(500),
    deadline        DATE,
    source          VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    submitted_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMPTZ,

    CONSTRAINT ck_campus_recruitments_source CHECK (source IN ('ADMIN', 'STUDENT')),
    CONSTRAINT ck_campus_recruitments_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX ix_campus_recruitments_open ON campus_recruitments (status, deadline);
