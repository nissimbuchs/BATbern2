-- =====================================================================================
-- Migration V82: Remove AGENDA_FINALIZED workflow state (8-state model)
-- =====================================================================================
-- Purpose: Remove the AGENDA_FINALIZED state to eliminate the manual gate that blocked
-- the automated event lifecycle pipeline. The AGENDA_PUBLISHED → EVENT_LIVE transition
-- is now handled directly by the scheduler (cron at 00:01 on event day).
--
-- Root cause: AGENDA_PUBLISHED → AGENDA_FINALIZED was the only manual transition between
-- agenda publication and the event going live. If missed, the entire automated tail
-- (EVENT_LIVE → EVENT_COMPLETED) never fired.
--
-- Changes:
-- 1. Migrate any events in agenda_finalized → agenda_published
-- 2. Remap task templates: agenda_finalized trigger → agenda_published
-- 3. Remap existing event_tasks: agenda_finalized trigger → agenda_published
-- 4. Update DB constraint to remove agenda_finalized
-- =====================================================================================

-- Step 1: Migrate any lingering agenda_finalized events → agenda_published
UPDATE events
SET workflow_state = 'agenda_published'
WHERE workflow_state = 'agenda_finalized';

-- Step 2: Remap task templates that triggered on agenda_finalized → agenda_published
-- (Affects: 'Newsletter: Final Agenda' and 'Catering Coordination' default templates)
UPDATE task_templates
SET trigger_state = 'agenda_published'
WHERE trigger_state = 'agenda_finalized';

-- Step 3: Remap any event_tasks already created with agenda_finalized trigger
UPDATE event_tasks
SET trigger_state = 'agenda_published'
WHERE trigger_state = 'agenda_finalized';

-- Step 4: Drop and recreate constraint without agenda_finalized (8-state model)
ALTER TABLE events DROP CONSTRAINT events_workflow_state_check;
ALTER TABLE events ADD CONSTRAINT events_workflow_state_check
CHECK (workflow_state IN (
    'created',
    'topic_selection',
    'speaker_identification',
    'slot_assignment',
    'agenda_published',
    'event_live',
    'event_completed',
    'archived'
));

-- Update column comment to reflect 8-state model
COMMENT ON COLUMN events.workflow_state IS
'Event workflow state (8-state model): created, topic_selection, speaker_identification, slot_assignment, agenda_published, event_live, event_completed, archived. AGENDA_FINALIZED removed in V82 - scheduler now transitions AGENDA_PUBLISHED directly to EVENT_LIVE on event day.';
