-- V12: Add ShedLock table for distributed scheduled task locking
-- Purpose: Prevent duplicate execution of scheduled tasks when multiple ECS instances are running
--
-- ShedLock uses this table to ensure only ONE instance executes a scheduled job at a time
-- Table schema: https://github.com/lukas-krecan/ShedLock#jdbctemplate

-- Create shedlock table
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,    -- Job name (unique identifier)
    lock_until TIMESTAMP NOT NULL,             -- Lock expires at this time
    locked_at TIMESTAMP NOT NULL,              -- When the lock was acquired
    locked_by VARCHAR(255) NOT NULL            -- Which instance holds the lock (hostname + thread)
);

-- Add index for lock expiry queries (improves performance when checking expired locks)
CREATE INDEX idx_shedlock_lock_until ON shedlock(lock_until);

-- Add comment to table
COMMENT ON TABLE shedlock IS 'ShedLock distributed lock table for scheduled tasks';
COMMENT ON COLUMN shedlock.name IS 'Unique job identifier (e.g., userReconciliationTask)';
COMMENT ON COLUMN shedlock.lock_until IS 'Lock expiration timestamp (job can be taken over after this time)';
COMMENT ON COLUMN shedlock.locked_at IS 'When the lock was acquired';
COMMENT ON COLUMN shedlock.locked_by IS 'Instance holding the lock (hostname + thread ID)';
