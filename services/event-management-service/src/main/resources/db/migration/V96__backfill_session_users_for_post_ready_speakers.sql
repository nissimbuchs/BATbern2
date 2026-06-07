-- Story 11.E.8: backfill Session + SessionUser rows for any speaker_pool row that reached
-- READY (or later non-terminal state) before the runReadyHook started provisioning these
-- rows automatically. Production has no such data yet — Epic 11 hasn't shipped — so this
-- migration is a no-op there. It exists to clean up local-dev databases (e.g. the
-- BATbern74 / testreferent1 scenario) without forcing developers to wipe their data.
--
-- Semantics:
--   * For each speaker_pool row with status IN (READY, INVITED, ACCEPTED, CONTENT_SUBMITTED,
--     QUALITY_REVIEWED) and session_id IS NULL and username IS NOT NULL:
--       1. Create a sessions row with a placeholder title ("TBD — <speaker_name>"|"<username>")
--          and a deterministic slug "<event_code>-<username>".
--       2. Create a session_users row linking that session to the speaker's username with
--          role = 'primary_speaker'. is_confirmed = true for ACCEPTED+, false otherwise.
--       3. Update speaker_pool.session_id to point at the new session.
--   * Slug collisions are resolved by appending a numeric suffix until unique.
--
-- This migration is idempotent in spirit (gated on session_id IS NULL) but is a one-shot
-- artefact — re-running it after a clean Flyway baseline would re-execute against any new
-- READY+ rows that lack a session, which is not the intent. Production deploys treat it as
-- a no-op via the WHERE clause.

DO $$
DECLARE
    sp_row RECORD;
    new_session_id UUID;
    slug_base TEXT;
    slug_candidate TEXT;
    slug_counter INT;
    is_accepted BOOLEAN;
BEGIN
    FOR sp_row IN
        SELECT sp.id,
               sp.event_id,
               sp.username,
               sp.speaker_name,
               sp.status,
               sp.accepted_at,
               e.event_code
        FROM speaker_pool sp
        JOIN events e ON e.id = sp.event_id
        WHERE sp.session_id IS NULL
          AND sp.username IS NOT NULL
          AND sp.username <> ''
          AND sp.status IN ('ready', 'invited', 'accepted', 'content_submitted', 'quality_reviewed')
    LOOP
        -- Build a deterministic slug base; lowercase, alphanumeric+hyphen only.
        slug_base := lower(sp_row.event_code || '-' || sp_row.username);
        slug_base := regexp_replace(slug_base, '[^a-z0-9-]', '-', 'g');
        slug_base := regexp_replace(slug_base, '-+', '-', 'g');
        slug_base := regexp_replace(slug_base, '^-|-$', '', 'g');
        IF slug_base = '' THEN
            slug_base := 'session-' || substr(sp_row.id::text, 1, 8);
        END IF;
        IF length(slug_base) > 190 THEN
            slug_base := substr(slug_base, 1, 190);
        END IF;

        slug_candidate := slug_base;
        slug_counter := 1;
        WHILE EXISTS (SELECT 1 FROM sessions WHERE session_slug = slug_candidate) LOOP
            slug_candidate := slug_base || '-' || slug_counter;
            slug_counter := slug_counter + 1;
            IF slug_counter > 1000 THEN
                RAISE EXCEPTION 'Unable to find unique slug for base %', slug_base;
            END IF;
        END LOOP;

        is_accepted := sp_row.status IN ('accepted', 'content_submitted', 'quality_reviewed');

        INSERT INTO sessions (
            id,
            event_id,
            event_code,
            session_slug,
            title,
            session_type,
            speaker_pool_id,
            materials_count,
            has_presentation,
            created_at,
            updated_at
        ) VALUES (
            uuid_generate_v4(),
            sp_row.event_id,
            sp_row.event_code,
            slug_candidate,
            COALESCE(NULLIF(sp_row.speaker_name, ''), 'TBD — ' || sp_row.username),
            'presentation',
            sp_row.id,
            0,
            FALSE,
            NOW(),
            NOW()
        )
        RETURNING id INTO new_session_id;

        INSERT INTO session_users (
            id,
            session_id,
            username,
            speaker_role,
            is_confirmed,
            invited_at,
            confirmed_at,
            created_at,
            updated_at
        ) VALUES (
            uuid_generate_v4(),
            new_session_id,
            sp_row.username,
            'primary_speaker',
            is_accepted,
            NOW(),
            CASE WHEN is_accepted THEN COALESCE(sp_row.accepted_at, NOW()) ELSE NULL END,
            NOW(),
            NOW()
        );

        UPDATE speaker_pool SET session_id = new_session_id WHERE id = sp_row.id;

        RAISE NOTICE 'Backfilled session % + session_users for speaker_pool % (%, status=%)',
            new_session_id, sp_row.id, sp_row.username, sp_row.status;
    END LOOP;
END $$;
