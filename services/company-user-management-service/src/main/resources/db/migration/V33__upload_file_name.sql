-- Story 17.11d: an upload keeps the original file name the client sent on create (or the base name
-- of an import), so a file attached later can show and download under it. Nullable: uploads made
-- before this migration have none.
ALTER TABLE company_user_uploads ADD COLUMN file_name VARCHAR(255);
