-- #1222 item 5: Swiss German ships as UI locale 'gsw-BE' (6 characters), which did not fit the
-- two-letter column and could not be saved as profile language. Widening a VARCHAR is a
-- metadata-only change in PostgreSQL (no rewrite), safe while the old task still runs: it only
-- ever writes two-letter codes. The allowed values are the users-api `language` enum (V15 dropped
-- the CHECK constraint).
ALTER TABLE user_profiles ALTER COLUMN pref_language TYPE VARCHAR(10);
COMMENT ON COLUMN user_profiles.pref_language IS
    'Preferred UI language, the users-api language enum (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE)';
