-- V15__remove_language_check_constraint.sql
-- Remove the pref_language CHECK constraint to allow all supported language codes.
-- Previously only 'de', 'en', 'fr', 'it' were allowed; the constraint is no longer needed
-- as validation is handled at the API layer via the OpenAPI enum definition.

DO $$
DECLARE
    v_constraint_name TEXT;
BEGIN
    SELECT tc.constraint_name INTO v_constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc
      ON tc.constraint_name = cc.constraint_name
    WHERE tc.table_name = 'user_profiles'
      AND tc.constraint_type = 'CHECK'
      AND cc.check_clause LIKE '%pref_language%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE user_profiles DROP CONSTRAINT ' || quote_ident(v_constraint_name);
        RAISE NOTICE 'Dropped constraint: %', v_constraint_name;
    ELSE
        RAISE NOTICE 'No pref_language constraint found, skipping.';
    END IF;
END $$;

-- Update column comment to reflect supported languages
COMMENT ON COLUMN user_profiles.pref_language IS 'Preferred language (de, en, fr, it, rm, es, fi, nl, ja)';
