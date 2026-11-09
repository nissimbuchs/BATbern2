-- V151 (owner decision 2026-11-09, 17.0 deferred work): 15 historical events (BATbern 2 to 54)
-- name their organizer with a username that contains an umlaut. No account can have such a
-- username (CUMS transliterates, chk_username_format), so these events pointed at nobody.
-- The owner identified the two people: Daniel Kühni and Andreas Grütter, both organizers. Each old
-- value moves to the account with that first and last name (umlaut or its transliteration) that
-- holds the ORGANIZER role, and only when exactly one account matches; otherwise the event keeps
-- its old value. user_profiles belongs to
-- company-user-management; all services share one database (V116 reads it the same way).
DO $$
DECLARE
    target TEXT;
    mapping RECORD;
BEGIN
    IF to_regclass('user_profiles') IS NULL OR to_regclass('role_assignments') IS NULL THEN
        RETURN;
    END IF;
    FOR mapping IN
        SELECT * FROM (VALUES
            ('daniel.kühni', 'daniel', ARRAY['kühni', 'kuehni', 'kuhni']),
            ('andreas.grütter', 'andreas', ARRAY['grütter', 'gruetter', 'grutter'])
        ) AS m(old_username, first_name, last_names)
    LOOP
        SELECT min(u.username) INTO target
        FROM user_profiles u
        JOIN role_assignments r ON r.user_id = u.id AND r.role = 'ORGANIZER'
        WHERE lower(u.first_name) = mapping.first_name
          AND lower(u.last_name) = ANY (mapping.last_names)
        HAVING count(*) = 1;

        IF target IS NOT NULL THEN
            UPDATE events SET organizer_username = target
            WHERE organizer_username = mapping.old_username;
        END IF;
    END LOOP;
END $$;
