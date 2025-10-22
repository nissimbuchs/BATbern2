-- Story 1.2.5: User Sync and Reconciliation Implementation
-- Create user_sync_compensation_log table for saga compensation pattern

-- Create user sync compensation log table
CREATE TABLE user_sync_compensation_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    cognito_id VARCHAR(255) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    target_role VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    compensation_required BOOLEAN DEFAULT false,
    compensation_executed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,

    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_operation CHECK (operation IN ('ROLE_SYNC', 'USER_CREATE', 'USER_DELETE'))
);

-- Indexes for fast lookups
CREATE INDEX idx_compensation_status ON user_sync_compensation_log(status, compensation_required)
    WHERE compensation_required = true;
CREATE INDEX idx_compensation_user ON user_sync_compensation_log(user_id);
CREATE INDEX idx_compensation_cognito ON user_sync_compensation_log(cognito_id);

-- Add comment
COMMENT ON TABLE user_sync_compensation_log IS 'Tracks saga compensation operations for user sync failures';
COMMENT ON COLUMN user_sync_compensation_log.operation IS 'Operation type: ROLE_SYNC, USER_CREATE, USER_DELETE';
COMMENT ON COLUMN user_sync_compensation_log.status IS 'Status: PENDING, COMPLETED, FAILED';
COMMENT ON COLUMN user_sync_compensation_log.compensation_required IS 'True if compensation is needed (Cognito sync failed)';
COMMENT ON COLUMN user_sync_compensation_log.retry_count IS 'Number of retry attempts';
