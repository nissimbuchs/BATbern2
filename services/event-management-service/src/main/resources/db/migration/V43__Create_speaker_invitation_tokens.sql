-- V43__Create_speaker_invitation_tokens.sql
-- Story 6.1a: Magic Link Infrastructure
-- Creates speaker_invitation_tokens table for passwordless speaker authentication
-- SOURCE OF TRUTH: docs/stories/6.1a-magic-link-infrastructure.md

-- Speaker invitation tokens for magic link authentication
-- Tokens are single-use for RESPOND actions, reusable for VIEW/SUBMIT
CREATE TABLE speaker_invitation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_pool_id UUID NOT NULL REFERENCES speaker_pool(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hash of token (plaintext never stored)
    action VARCHAR(20) NOT NULL,             -- RESPOND, SUBMIT, VIEW
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,        -- NULL if not yet used (single-use tokens)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_token_action CHECK (action IN ('RESPOND', 'SUBMIT', 'VIEW'))
);

-- Index for fast token lookup by hash (primary validation path)
CREATE INDEX idx_invitation_tokens_hash ON speaker_invitation_tokens(token_hash);

-- Index for finding all tokens for a speaker (admin operations)
CREATE INDEX idx_invitation_tokens_speaker ON speaker_invitation_tokens(speaker_pool_id);

-- Partial index for cleanup job - only active (unused, not expired) tokens
CREATE INDEX idx_invitation_tokens_expires ON speaker_invitation_tokens(expires_at)
    WHERE used_at IS NULL;

-- Trigger for automatic updated_at timestamps
-- Note: No updated_at column since tokens are immutable after creation (only used_at changes)

-- Comments documenting architecture alignment
COMMENT ON TABLE speaker_invitation_tokens IS 'Magic link tokens for speaker portal authentication - Story 6.1a';
COMMENT ON COLUMN speaker_invitation_tokens.token_hash IS 'SHA-256 hash of token - plaintext is NEVER stored (security requirement AC6)';
COMMENT ON COLUMN speaker_invitation_tokens.action IS 'Token action type: RESPOND (single-use), SUBMIT/VIEW (reusable) - see AC3';
COMMENT ON COLUMN speaker_invitation_tokens.used_at IS 'Timestamp when single-use token was consumed - NULL means unused';
COMMENT ON COLUMN speaker_invitation_tokens.expires_at IS 'Token expiry time - default 30 days from creation (AC4)';
