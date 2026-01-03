-- V29: Refactor speaker_status_history FK from event_code to event_id
-- Story: Bugfix for PATCH /api/v1/events/{eventCode} foreign key constraint violation
--
-- Problem: speaker_status_history uses event_code as FK without ON UPDATE CASCADE,
-- causing 500 errors when updating event numbers.
--
-- Solution: Migrate to event_id (UUID) to align with other tables and support
-- eventCode updates. Use ON DELETE CASCADE for schema consistency.

-- Step 1: Drop existing FK constraint on event_code
ALTER TABLE speaker_status_history
DROP CONSTRAINT IF EXISTS fk_speaker_status_history_event_code;

-- Step 2: Add event_id column
ALTER TABLE speaker_status_history
ADD COLUMN event_id UUID;

-- Step 3: Populate event_id from event_code for existing records
UPDATE speaker_status_history ssh
SET event_id = e.id
FROM events e
WHERE ssh.event_code = e.event_code;

-- Step 4: Make event_id NOT NULL (all records should now have event_id)
ALTER TABLE speaker_status_history
ALTER COLUMN event_id SET NOT NULL;

-- Step 5: Add FK constraint with CASCADE behavior (consistent with other tables)
ALTER TABLE speaker_status_history
ADD CONSTRAINT fk_speaker_status_history_event_id
    FOREIGN KEY (event_id)
    REFERENCES events(id)
    ON UPDATE CASCADE
    ON DELETE CASCADE;

-- Step 6: Create index for FK performance
CREATE INDEX idx_speaker_status_history_event_id
ON speaker_status_history(event_id);

-- Step 7: Drop event_code column (no longer needed)
ALTER TABLE speaker_status_history
DROP COLUMN event_code;

-- Add comment explaining the change
COMMENT ON CONSTRAINT fk_speaker_status_history_event_id ON speaker_status_history IS
'FK to events using UUID for consistency with other tables. ON DELETE CASCADE aligns with schema design pattern.';
