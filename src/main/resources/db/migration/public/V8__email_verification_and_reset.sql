-- NULL = the owner of this address hasn't proven they can read its inbox yet.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Accounts created before verification existed keep working (grandfathered as verified).
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;

-- Single-use links sent by email (verify address, reset password). Only SHA-256(raw) is stored,
-- same as refresh tokens: a leaked DB dump yields no usable links.
CREATE TABLE email_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    VARCHAR(20) NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_email_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_tokens_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);

CREATE INDEX ix_email_tokens_user_purpose ON email_tokens (user_id, purpose);
