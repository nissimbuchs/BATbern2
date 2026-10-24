-- Organizer cockpit plan (docs/plans/organizer-cockpit-attention-cards.md), Issue A, decided 2026-10-24.
--
-- 1. activation_lead_days (T4): a pending task becomes 'todo' at whichever comes first — its
--    trigger state, or (due_date - activation_lead_days). NULL = trigger state only.
--    Post-event templates keep NULL so e.g. "Slides Are Online" never surfaces before the event.
-- 2. system_key: a stable identifier for the default templates the code must recognise
--    (supersession, T7). Names can be edited; the key cannot. Set once here, by name.
-- 3. Default template data (default templates are read-only through the API, so these
--    organizer decisions can only land as a migration):
--    - Speaker Lineup newsletter triggers when the speakers phase is published (T5)
--    - Catering Coordination due 5 days before the event (was -30)
--    - two new defaults: "Namensschilder" (-2 days) and "Wein" (event day)
-- 4. Existing pending tasks inherit the lead days of their template, and pending Speaker
--    Lineup tasks follow the new trigger.

ALTER TABLE task_templates ADD COLUMN IF NOT EXISTS activation_lead_days INTEGER DEFAULT 14;
ALTER TABLE task_templates ADD COLUMN IF NOT EXISTS system_key VARCHAR(50);
ALTER TABLE event_tasks ADD COLUMN IF NOT EXISTS activation_lead_days INTEGER;

ALTER TABLE task_templates ADD CONSTRAINT task_templates_activation_lead_days_check
    CHECK (activation_lead_days IS NULL OR activation_lead_days >= 0);
CREATE UNIQUE INDEX IF NOT EXISTS uq_task_templates_system_key
    ON task_templates(system_key) WHERE system_key IS NOT NULL;

UPDATE task_templates SET activation_lead_days = NULL
WHERE trigger_state IN ('event_live', 'event_completed', 'archived');

UPDATE task_templates SET system_key = 'newsletter_topic'
WHERE is_default = true AND name = 'Newsletter: Topic Announcement';

UPDATE task_templates SET system_key = 'newsletter_speakers', trigger_state = 'speakers_published'
WHERE is_default = true AND name = 'Newsletter: Speaker Lineup';

UPDATE task_templates SET due_date_offset_days = -5
WHERE is_default = true AND name = 'Catering Coordination';

INSERT INTO task_templates (name, trigger_state, due_date_type, due_date_offset_days, is_default, activation_lead_days)
SELECT 'Namensschilder', 'agenda_published', 'relative_to_event', -2, true, 14
WHERE NOT EXISTS (SELECT 1 FROM task_templates WHERE name = 'Namensschilder');

INSERT INTO task_templates (name, trigger_state, due_date_type, due_date_offset_days, is_default, activation_lead_days)
SELECT 'Wein', 'agenda_published', 'relative_to_event', 0, true, 14
WHERE NOT EXISTS (SELECT 1 FROM task_templates WHERE name = 'Wein');

UPDATE event_tasks t SET activation_lead_days = tt.activation_lead_days
FROM task_templates tt
WHERE t.template_id = tt.id AND t.status = 'pending';

UPDATE event_tasks t SET trigger_state = 'speakers_published'
FROM task_templates tt
WHERE t.template_id = tt.id AND tt.system_key = 'newsletter_speakers' AND t.status = 'pending';

COMMENT ON COLUMN task_templates.activation_lead_days IS
    'Days before due_date at which a pending task activates even if its trigger state was not reached; NULL = trigger only';
COMMENT ON COLUMN task_templates.system_key IS
    'Stable key for default templates the code recognises (e.g. newsletter supersession); NULL for custom templates';
COMMENT ON COLUMN event_tasks.activation_lead_days IS
    'Copied from the template at creation; see task_templates.activation_lead_days';
