-- Story 10.18: Event Archival Task & Notification Cleanup
-- Adds cancellation tracking fields and expands status constraint to include 'cancelled'.

ALTER TABLE event_tasks
    ADD COLUMN IF NOT EXISTS cancelled_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cancelled_at     TIMESTAMPTZ;

-- Expand status constraint to include 'cancelled' (archival cleanup)
ALTER TABLE event_tasks DROP CONSTRAINT IF EXISTS event_tasks_status_check;
ALTER TABLE event_tasks ADD CONSTRAINT event_tasks_status_check
    CHECK (status IN ('pending', 'todo', 'in_progress', 'completed', 'cancelled'));

COMMENT ON CONSTRAINT event_tasks_status_check ON event_tasks IS
    'Valid task statuses: pending, todo, in_progress, completed, cancelled (archived event cleanup)';
