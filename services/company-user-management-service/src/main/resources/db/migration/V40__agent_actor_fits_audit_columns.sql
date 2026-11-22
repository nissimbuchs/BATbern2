-- Agentic interaction, story 0.2: who-acted columns record the actor
-- (ch.batbern.shared.security.Actors): a username, `agent:<run> for <username>`
-- (up to 147 characters) or `system:<job>`. The audit columns narrower than 255 are widened.
-- Widening a VARCHAR is a catalog-only change in PostgreSQL; no view depends on these columns.
ALTER TABLE profile_merge_requests ALTER COLUMN decided_by TYPE VARCHAR(255);
ALTER TABLE company_logo_requests ALTER COLUMN decided_by TYPE VARCHAR(255);
ALTER TABLE user_duplicate_dismissals ALTER COLUMN dismissed_by TYPE VARCHAR(255);
