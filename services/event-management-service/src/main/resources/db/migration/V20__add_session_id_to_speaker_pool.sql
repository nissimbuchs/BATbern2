-- V20: Add session_id FK to speaker_pool to link speakers with their allocated session
-- Story 5.4 TODO Resolution - IMPL-001

-- Add session_id column (nullable initially for existing data)
ALTER TABLE speaker_pool
ADD COLUMN session_id UUID;

-- Add foreign key constraint with ON DELETE SET NULL
-- When session is deleted, set speaker_pool.session_id to NULL (speaker remains in pool)
ALTER TABLE speaker_pool
ADD CONSTRAINT fk_speaker_pool_session
    FOREIGN KEY (session_id)
    REFERENCES sessions(id)
    ON DELETE SET NULL;

-- Add index for faster lookups
CREATE INDEX idx_speaker_pool_session_id ON speaker_pool(session_id);

-- Add comment for documentation
COMMENT ON COLUMN speaker_pool.session_id IS
'References sessions.id - links speaker to their allocated session slot. Created as empty placeholder when speaker added to pool, updated as planning progresses.';
