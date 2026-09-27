-- Every club is on a plan; the plan decides limits and which features are included.
ALTER TABLE tenants ADD COLUMN plan VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE tenants ADD CONSTRAINT ck_tenants_plan CHECK (plan IN ('FREE', 'PRO'));

-- Per-club overrides of the plan's features (e.g. a pilot club gets posters on FREE).
-- No row = the plan's default applies.
CREATE TABLE tenant_features (
    tenant_id  UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    feature    VARCHAR(40) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, feature)
);
