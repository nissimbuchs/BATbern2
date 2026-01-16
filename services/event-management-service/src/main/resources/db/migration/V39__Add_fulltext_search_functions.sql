-- V39__Add_fulltext_search_functions.sql
-- Bug Fix: Add missing PostgreSQL full-text search helper function
--
-- Context:
-- EventSearchService.java calls PostgreSQL functions for full-text search,
-- but these functions were never created in the database.
--
-- Solution:
-- Create a generic ts_match function that wraps the @@ operator.
-- This function can be called from JPA CriteriaBuilder with both
-- the tsvector column and the tsquery as parameters.
--
-- Usage Example:
--   SELECT * FROM events WHERE ts_match(title_vector, to_tsquery('german', 'search'));

-- Create generic full-text search match function
-- Accepts: tsvector (the search vector), tsquery (the search query)
-- Returns: boolean (true if vector matches query)
CREATE OR REPLACE FUNCTION ts_match(vec tsvector, query tsquery)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
  SELECT vec @@ query;
$$;

-- Add function comment
COMMENT ON FUNCTION ts_match(tsvector, tsquery) IS
    'Generic full-text search match function. Wraps PostgreSQL @@ operator for use in JPA Criteria API queries.';

-- Log migration for audit trail
DO $$
BEGIN
    RAISE NOTICE 'V39 Migration Complete: Added ts_match() function for full-text search';
    RAISE NOTICE 'EventSearchService.java must be updated to pass vector columns to ts_match()';
END $$;
