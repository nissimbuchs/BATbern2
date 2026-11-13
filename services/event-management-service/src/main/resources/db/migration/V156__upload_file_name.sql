-- Story 17.11d: an upload keeps the original file name the client sent on create (or the base name
-- of an import), so a session material keeps it (it was sent again by the client before). Nullable:
-- uploads made before this migration have none.
ALTER TABLE event_management_uploads ADD COLUMN file_name VARCHAR(255);
