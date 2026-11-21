-- Agentic interaction, story 0.2: who-acted columns record the actor
-- (ch.batbern.shared.security.Actors): a username, `agent:<run> for <username>`
-- (up to 147 characters) or `system:<job>`. The audit columns narrower than 255 are widened.
-- Widening a VARCHAR is a catalog-only change in PostgreSQL; no view depends on these columns.
ALTER TABLE app_settings ALTER COLUMN updated_by TYPE VARCHAR(255);
ALTER TABLE task_templates ALTER COLUMN created_by_username TYPE VARCHAR(255);
ALTER TABLE event_tasks ALTER COLUMN completed_by_username TYPE VARCHAR(255);
ALTER TABLE speaker_status_history ALTER COLUMN changed_by_username TYPE VARCHAR(255);
ALTER TABLE mail_conversation ALTER COLUMN updated_by TYPE VARCHAR(255);
ALTER TABLE speaker_arrivals ALTER COLUMN confirmed_by_username TYPE VARCHAR(255);
ALTER TABLE sessions ALTER COLUMN completed_by_username TYPE VARCHAR(255);
ALTER TABLE user_merge_log ALTER COLUMN performed_by TYPE VARCHAR(255);
