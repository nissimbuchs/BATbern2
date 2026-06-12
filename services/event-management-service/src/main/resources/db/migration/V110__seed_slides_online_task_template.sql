-- Story 7.3: "The Slides Are Online" Mail
-- Seed a new DEFAULT task template that prompts the organizer to send the slides-online
-- mail to the event's active registrants once the event has completed.
--
-- Trigger / timing (Nissim, 2026-06-10):
--   trigger_state        = 'event_completed'   -- task surfaces (pending → todo) once the event completes
--   due_date_type        = 'relative_to_event'
--   due_date_offset_days = 1                    -- due ONE DAY AFTER the event date (slides go up post-event)
--   is_default           = true                 -- auto-created with every new event's task set
--
-- No task-engine change is needed: EventTaskService.createTasksForEvent(...) instantiates all
-- default templates uniformly at event creation, and autoCreateTasksForState(...) flips this one
-- to 'todo' when the event reaches EVENT_COMPLETED. Mirrors the existing V22 newsletter seeds
-- (e.g. 'Newsletter: Final Agenda', relative_to_event, -14) — only the sign/offset differs.
--
-- Idempotent: guarded by NOT EXISTS so a re-run (or a name collision in a hand-seeded env) is a no-op.
INSERT INTO task_templates (name, trigger_state, due_date_type, due_date_offset_days, is_default)
SELECT 'Newsletter: Slides Are Online', 'event_completed', 'relative_to_event', 1, true
WHERE NOT EXISTS (
    SELECT 1 FROM task_templates WHERE name = 'Newsletter: Slides Are Online'
);
