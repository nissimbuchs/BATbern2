-- =====================================================================================
-- Migration V30: Consolidate Event Workflow States (16 → 9 States)
-- =====================================================================================
-- Purpose: Consolidate event workflow from 16-state model to 9-state architectural model
--
-- State Consolidation Mapping:
-- - SPEAKER_BRAINSTORMING, SPEAKER_OUTREACH, SPEAKER_CONFIRMATION, CONTENT_COLLECTION,
--   QUALITY_REVIEW, THRESHOLD_CHECK, OVERFLOW_MANAGEMENT → SPEAKER_IDENTIFICATION
-- - NEWSLETTER_SENT, EVENT_READY → AGENDA_FINALIZED
-- - PARTNER_MEETING_COMPLETE → ARCHIVED
--
-- New States Added:
-- - EVENT_LIVE (for cron-based auto-transition when event date reached)
-- - EVENT_COMPLETED (for cron-based auto-transition when event date passed)
--
-- Rollback Strategy:
-- - Migration log table created with all state changes for audit and rollback
-- - workflow_state_old column retained temporarily for verification
-- =====================================================================================

-- Step 1: Create migration audit log for rollback capability
-- =====================================================================================
CREATE TABLE IF NOT EXISTS workflow_state_migration_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    old_state VARCHAR(50) NOT NULL,
    new_state VARCHAR(50) NOT NULL,
    migrated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    migration_version VARCHAR(20) DEFAULT 'V30'
);

CREATE INDEX IF NOT EXISTS idx_workflow_migration_log_event_id
ON workflow_state_migration_log(event_id);

CREATE INDEX IF NOT EXISTS idx_workflow_migration_log_migrated_at
ON workflow_state_migration_log(migrated_at);

COMMENT ON TABLE workflow_state_migration_log IS
'Audit log for workflow state migrations - used for rollback and compliance';

-- Step 2: Backup current workflow states
-- =====================================================================================
ALTER TABLE events ADD COLUMN IF NOT EXISTS workflow_state_old VARCHAR(50);

UPDATE events
SET workflow_state_old = workflow_state
WHERE workflow_state_old IS NULL;

-- Step 3: Consolidate 7 speaker-related states into SPEAKER_IDENTIFICATION
-- =====================================================================================
-- These states are consolidated because they represent sub-phases of speaker management
-- rather than distinct workflow states. Speaker progress is tracked in speaker_pool table.
UPDATE events
SET workflow_state = 'speaker_identification'
WHERE workflow_state IN (
    'speaker_brainstorming',   -- Brainstorming potential speakers
    'speaker_outreach',        -- Reaching out to identified speakers
    'speaker_confirmation',    -- Confirming speaker participation
    'content_collection',      -- Collecting presentation materials
    'quality_review',          -- Reviewing submitted content
    'threshold_check',         -- Checking minimum speaker threshold
    'overflow_management'      -- Managing speaker overflow
);

-- Step 4: Migrate removed task-based states
-- =====================================================================================
-- NEWSLETTER_SENT and EVENT_READY are tasks, not workflow states
-- These become tasks in the task management system instead
UPDATE events
SET workflow_state = 'agenda_finalized'
WHERE workflow_state IN ('newsletter_sent', 'event_ready');

-- PARTNER_MEETING_COMPLETE is a post-event task, not a separate workflow state
UPDATE events
SET workflow_state = 'archived'
WHERE workflow_state = 'partner_meeting_complete';

-- Step 5: Log all migrations to audit table
-- =====================================================================================
INSERT INTO workflow_state_migration_log (event_id, old_state, new_state, migrated_at)
SELECT
    id AS event_id,
    workflow_state_old AS old_state,
    workflow_state AS new_state,
    CURRENT_TIMESTAMP AS migrated_at
FROM events
WHERE workflow_state_old IS NOT NULL
  AND workflow_state_old != workflow_state;

-- Step 6: Update database constraint to enforce 9-state model
-- =====================================================================================
-- Drop old constraint if it exists
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_workflow_state_check;

-- Add new constraint for 9-state model
ALTER TABLE events ADD CONSTRAINT events_workflow_state_check
CHECK (workflow_state IN (
    'created',                  -- Event created, no topic yet
    'topic_selection',          -- Topic selection in progress
    'speaker_identification',   -- Building speaker pool, managing speaker workflow
    'slot_assignment',          -- Assigning speakers to time slots
    'agenda_published',         -- Agenda published to attendees
    'agenda_finalized',         -- Agenda locked, no more changes
    'event_live',               -- Event currently happening (NEW - for cron)
    'event_completed',          -- Event completed, post-processing (NEW - for cron)
    'archived'                  -- Event archived (terminal state)
));

-- Step 7: Verification queries (logged for monitoring)
-- =====================================================================================
-- Count events by new workflow state
DO $$
DECLARE
    v_created_count INT;
    v_topic_selection_count INT;
    v_speaker_identification_count INT;
    v_slot_assignment_count INT;
    v_agenda_published_count INT;
    v_agenda_finalized_count INT;
    v_event_live_count INT;
    v_event_completed_count INT;
    v_archived_count INT;
    v_total_count INT;
    v_migrated_count INT;
BEGIN
    SELECT COUNT(*) INTO v_created_count FROM events WHERE workflow_state = 'created';
    SELECT COUNT(*) INTO v_topic_selection_count FROM events WHERE workflow_state = 'topic_selection';
    SELECT COUNT(*) INTO v_speaker_identification_count FROM events WHERE workflow_state = 'speaker_identification';
    SELECT COUNT(*) INTO v_slot_assignment_count FROM events WHERE workflow_state = 'slot_assignment';
    SELECT COUNT(*) INTO v_agenda_published_count FROM events WHERE workflow_state = 'agenda_published';
    SELECT COUNT(*) INTO v_agenda_finalized_count FROM events WHERE workflow_state = 'agenda_finalized';
    SELECT COUNT(*) INTO v_event_live_count FROM events WHERE workflow_state = 'event_live';
    SELECT COUNT(*) INTO v_event_completed_count FROM events WHERE workflow_state = 'event_completed';
    SELECT COUNT(*) INTO v_archived_count FROM events WHERE workflow_state = 'archived';
    SELECT COUNT(*) INTO v_total_count FROM events;
    SELECT COUNT(*) INTO v_migrated_count FROM workflow_state_migration_log WHERE migration_version = 'V30';

    RAISE NOTICE '=== V30 Migration Results ===';
    RAISE NOTICE 'Total events: %', v_total_count;
    RAISE NOTICE 'Events migrated: %', v_migrated_count;
    RAISE NOTICE '--- State Distribution ---';
    RAISE NOTICE 'CREATED: %', v_created_count;
    RAISE NOTICE 'TOPIC_SELECTION: %', v_topic_selection_count;
    RAISE NOTICE 'SPEAKER_IDENTIFICATION: %', v_speaker_identification_count;
    RAISE NOTICE 'SLOT_ASSIGNMENT: %', v_slot_assignment_count;
    RAISE NOTICE 'AGENDA_PUBLISHED: %', v_agenda_published_count;
    RAISE NOTICE 'AGENDA_FINALIZED: %', v_agenda_finalized_count;
    RAISE NOTICE 'EVENT_LIVE: %', v_event_live_count;
    RAISE NOTICE 'EVENT_COMPLETED: %', v_event_completed_count;
    RAISE NOTICE 'ARCHIVED: %', v_archived_count;
    RAISE NOTICE '===========================';
END $$;

-- Step 8: Add comments for documentation
-- =====================================================================================
COMMENT ON COLUMN events.workflow_state IS
'Event workflow state (9-state model): created, topic_selection, speaker_identification, slot_assignment, agenda_published, agenda_finalized, event_live, event_completed, archived';

COMMENT ON COLUMN events.workflow_state_old IS
'Backup of workflow state before V30 migration (16-state model) - retained for rollback capability';

-- =====================================================================================
-- Migration Complete
-- =====================================================================================
-- Next Steps:
-- 1. Verify migration results in application logs (RAISE NOTICE output)
-- 2. Run integration tests to verify application behavior with 9-state model
-- 3. Monitor workflow_state_migration_log for audit trail
-- 4. After verification (7-14 days), can optionally drop workflow_state_old column
--
-- Rollback Procedure (if needed):
-- 1. Stop application
-- 2. Execute: UPDATE events SET workflow_state = workflow_state_old WHERE workflow_state_old IS NOT NULL;
-- 3. Execute: ALTER TABLE events DROP CONSTRAINT events_workflow_state_check;
-- 4. Execute: (re-add old 16-state constraint)
-- 5. Clear migration log: DELETE FROM workflow_state_migration_log WHERE migration_version = 'V30';
-- 6. Restart application with old enum
-- =====================================================================================
