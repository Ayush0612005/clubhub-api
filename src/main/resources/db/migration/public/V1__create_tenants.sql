-- Platform-level registry of clubs (tenants). Lives in the shared "public" schema.
CREATE TABLE tenants (
    id          UUID         PRIMARY KEY,
    slug        VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    schema_name VARCHAR(63)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_tenants_slug        UNIQUE (slug),
    CONSTRAINT uk_tenants_schema_name UNIQUE (schema_name),
    -- slug becomes part of a schema name, so the DB itself refuses anything unsafe
    CONSTRAINT ck_tenants_slug        CHECK (slug ~ '^[a-z][a-z0-9_]{2,39}$'),
    CONSTRAINT ck_tenants_status      CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);
