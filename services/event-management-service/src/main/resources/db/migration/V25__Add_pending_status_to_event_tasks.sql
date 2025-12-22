-- V24: Add 'pending' status to event_tasks constraint (Story 5.5 AC21)
--
-- AC21 requires tasks to start in 'pending' state and be activated to 'todo'
-- when the event reaches the trigger state.

-- Drop existing check constraint
ALTER TABLE event_tasks DROP CONSTRAINT IF EXISTS event_tasks_status_check;

-- Add new check constraint with 'pending' status
ALTER TABLE event_tasks ADD CONSTRAINT event_tasks_status_check
  CHECK (status IN ('pending', 'todo', 'in_progress', 'completed'));

COMMENT ON CONSTRAINT event_tasks_status_check ON event_tasks IS
  'Valid task statuses: pending (created but not yet active), todo (active/ready to work), in_progress (being worked on), completed (finished)';
