-- Who belongs to which club, and with what role. A user can be a MEMBER of one club
-- and CLUB_ADMIN of another; the role only ever applies inside that one club.
CREATE TABLE memberships (
    id        UUID        PRIMARY KEY,
    user_id   UUID        NOT NULL REFERENCES users (id)   ON DELETE CASCADE,
    tenant_id UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_memberships_user_tenant UNIQUE (user_id, tenant_id),
    CONSTRAINT ck_memberships_role CHECK (role IN ('CLUB_ADMIN', 'CORE', 'MEMBER'))
);

-- PostgreSQL does NOT index foreign-key columns automatically.
-- (user_id, tenant_id) is already covered by the unique constraint's index;
-- "all members of a club" lookups need their own index on tenant_id.
CREATE INDEX ix_memberships_tenant ON memberships (tenant_id);
