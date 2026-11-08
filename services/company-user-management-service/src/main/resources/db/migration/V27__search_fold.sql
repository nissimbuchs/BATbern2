-- One search fold for every search, evaluated in SQL on both sides.
--
-- batbern_fold(text): lower case, diacritics removed (unaccent, which also turns ß into ss),
-- ae/oe/ue read as a/o/u, hyphen/apostrophe/underscore as a space, whitespace collapsed.
-- A search term matches when batbern_fold(field) LIKE '%' || batbern_fold(term) || '%'.
--
-- All services share one database and the CUMS and EMS migrations both create this function.
-- The body below is byte-identical to shared-kernel src/main/resources/db/search/batbern_fold.sql;
-- SearchFoldContract (test fixture) fails the build of a service whose copy differs.
-- The advisory lock serialises the two migrations when both services start at the same time
-- (concurrent CREATE EXTENSION / CREATE OR REPLACE FUNCTION would fail with a unique violation
-- or "tuple concurrently updated"). Lock key: hashtext('batbern_fold').
SELECT pg_advisory_xact_lock(hashtext('batbern_fold'));

CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION batbern_fold(input text) RETURNS text
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT regexp_replace(replace(replace(replace(translate(lower(public.unaccent(coalesce(input, ''))),
         '-''_', '   '), 'ae', 'a'), 'oe', 'o'), 'ue', 'u'), '\s+', ' ', 'g')
$$;
