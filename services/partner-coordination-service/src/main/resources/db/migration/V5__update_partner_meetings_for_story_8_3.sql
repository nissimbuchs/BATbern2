-- V5__update_partner_meetings_for_story_8_3.sql
-- Story 8.3: Partner Meeting Coordination
--
-- Alters the partner_meetings table from the original V2 schema to match Story 8.3
-- requirements. Changes:
--   - Rename scheduled_date → meeting_date
--   - Update meeting_type CHECK to uppercase (SPRING | AUTUMN)
--   - Add event_code (ADR-003: link by code, not UUID)
--   - Add start_time, end_time (the meeting time slot)
--   - Add notes (post-meeting free text)
--   - Add invite_sent_at (timestamp when ICS email was sent)
--   - Add created_by (organizer username, ADR-003)
--   - Drop materials_generated (not needed per Story 8.3 scope)
--
-- ADR-003: event_code and created_by use meaningful string IDs (no UUID FK)

-- 1. Rename scheduled_date → meeting_date
ALTER TABLE partner_meetings RENAME COLUMN scheduled_date TO meeting_date;

-- 2. Add new required columns
ALTER TABLE partner_meetings
    ADD COLUMN IF NOT EXISTS event_code    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS start_time    TIME,
    ADD COLUMN IF NOT EXISTS end_time      TIME,
    ADD COLUMN IF NOT EXISTS notes         TEXT,
    ADD COLUMN IF NOT EXISTS invite_sent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS created_by    VARCHAR(100);

-- 3. Set sensible defaults for existing rows (should be none in prod at this point)
UPDATE partner_meetings SET event_code = 'UNKNOWN' WHERE event_code IS NULL;
UPDATE partner_meetings SET start_time = '12:00:00'  WHERE start_time IS NULL;
UPDATE partner_meetings SET end_time   = '14:00:00'  WHERE end_time   IS NULL;
UPDATE partner_meetings SET created_by = 'system'    WHERE created_by IS NULL;

-- 4. Now make the columns NOT NULL
ALTER TABLE partner_meetings
    ALTER COLUMN event_code  SET NOT NULL,
    ALTER COLUMN start_time  SET NOT NULL,
    ALTER COLUMN end_time    SET NOT NULL,
    ALTER COLUMN created_by  SET NOT NULL;

-- 5. Drop the old CHECK constraint on meeting_type and add uppercase version
ALTER TABLE partner_meetings DROP CONSTRAINT IF EXISTS partner_meetings_meeting_type_check;
ALTER TABLE partner_meetings
    ADD CONSTRAINT partner_meetings_meeting_type_check
    CHECK (meeting_type IN ('SPRING', 'AUTUMN'));

-- 6. Update any existing lowercase values from V2
UPDATE partner_meetings SET meeting_type = UPPER(meeting_type)
WHERE meeting_type IN ('spring', 'autumn', 'ad_hoc');

-- 7. Drop materials_generated (not used in Story 8.3 scope)
ALTER TABLE partner_meetings DROP COLUMN IF EXISTS materials_generated;

-- 8. Add indexes for new columns
CREATE INDEX IF NOT EXISTS idx_partner_meetings_event_code ON partner_meetings(event_code);

-- Note: idx_partner_meetings_meeting_type already exists from V2
