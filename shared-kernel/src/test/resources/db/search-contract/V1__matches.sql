-- test copy
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION batbern_fold(input text) RETURNS text
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT regexp_replace(replace(replace(replace(translate(lower(public.unaccent(coalesce(input, ''))),
         '-''_', '   '), 'ae', 'a'), 'oe', 'o'), 'ue', 'u'), '\s+', ' ', 'g')
$$;
