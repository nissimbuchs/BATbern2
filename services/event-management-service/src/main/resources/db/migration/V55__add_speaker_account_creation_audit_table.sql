-- Story 9.2 AC4: Speaker Account Creation Audit Table
-- Tracks Cognito account creation/role extension events for audit and debugging purposes.
-- email_hash uses SHA-256 to avoid storing PII in logs while enabling audit queries.

-- Note: speaker_pool_id has no FK constraint to speaker_pool intentionally.
-- Audit records must survive even if the speaker_pool row is deleted (e.g. data cleanup),
-- and cross-service FK constraints are avoided per ADR-003 (service-per-schema isolation).
CREATE TABLE speaker_account_creation_audit (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_pool_id UUID        NOT NULL,
    email_hash      VARCHAR(64) NOT NULL,   -- SHA-256 hex of email (PII-safe)
    cognito_user_id VARCHAR(255),           -- Cognito username/sub (null if creation failed)
    action          VARCHAR(10) NOT NULL CHECK (action IN ('NEW', 'EXTENDED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_speaker_account_audit_speaker_pool_id
    ON speaker_account_creation_audit (speaker_pool_id);

CREATE INDEX idx_speaker_account_audit_email_hash
    ON speaker_account_creation_audit (email_hash);

COMMENT ON TABLE speaker_account_creation_audit IS
    'Story 9.2 AC4: Audit trail for speaker Cognito account creation and role extension events';

COMMENT ON COLUMN speaker_account_creation_audit.email_hash IS
    'SHA-256 hex digest of the speaker email — stored instead of plain email to avoid PII in logs';

COMMENT ON COLUMN speaker_account_creation_audit.action IS
    'NEW = Cognito account created; EXTENDED = SPEAKER role added to existing account';
