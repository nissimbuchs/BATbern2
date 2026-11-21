-- =====================================================================================
-- V160: CANCELLED workflow state (#824 item 2)
-- =====================================================================================
-- An organizer can call an event off (Event Detail, Settings, Danger Zone). The event moves to
-- 'cancelled', off the regular path: reachable from every state before event_completed, terminal.
-- Only the check constraint changes; no existing row is touched.
-- =====================================================================================

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
    'archived',
    'cancelled'
));

COMMENT ON COLUMN events.workflow_state IS
'Event workflow state (8-state model plus cancelled): created, topic_selection, speaker_identification, slot_assignment, agenda_published, event_live, event_completed, archived; cancelled (V160, #824) is reachable from every state before event_completed and terminal.';
