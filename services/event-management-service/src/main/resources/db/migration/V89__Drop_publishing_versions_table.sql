-- Drop unused publishing_versions table
-- Version control feature (version history + rollback) has been removed from the codebase.
DROP TABLE IF EXISTS publishing_versions;
