-- V108: fix session_content_history.session_id FK to cascade-delete with its session.
--
-- Bug (surfaced 2026-05-31 by the speaker-pool golden-path E2E): deleting an event that has any
-- submitted speaker content fails with HTTP 500:
--   ERROR: null value in column "session_id" of relation "session_content_history"
--          violates not-null constraint
--   SQL:   UPDATE ONLY session_content_history SET session_id = NULL WHERE session_id = $1
--          [delete from events where id=? and version=?]
--
-- Root cause: the session_id FK is ON DELETE SET NULL, but V99 enforced session_id NOT NULL
-- (and dropped the old speaker_pool_id ON DELETE CASCADE that used to clean these rows). So when
-- a session is deleted — e.g. via the events.sessions ON DELETE CASCADE chain on event delete —
-- the DB tries to NULL session_content_history.session_id and violates the NOT NULL constraint,
-- which 500s the whole delete. Net effect since V99: no content-bearing event can be deleted.
--
-- Fix: session_content_history is session-scoped audit data, so it should be removed with its
-- session — switch the FK to ON DELETE CASCADE. The constraint kept its pre-rename name
-- (speaker_content_submissions_session_id_fkey), but we discover it by column so this is robust
-- across environments regardless of the actual name. Column alias-qualified per coding standards.
DO $$
DECLARE
  v_fk_name text;
BEGIN
  -- Find the FK on session_id by membership (att.attnum = ANY(con.conkey)) — robust regardless
  -- of the constraint's name (it kept the pre-rename name speaker_content_submissions_*).
  SELECT con.conname
    INTO v_fk_name
    FROM pg_constraint con
    JOIN pg_attribute att
      ON att.attrelid = con.conrelid
     AND att.attnum = ANY (con.conkey)
   WHERE con.conrelid = 'public.session_content_history'::regclass
     AND con.contype = 'f'
     AND att.attname = 'session_id'
   LIMIT 1;

  IF v_fk_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE public.session_content_history DROP CONSTRAINT %I', v_fk_name);
  END IF;

  ALTER TABLE public.session_content_history
    ADD CONSTRAINT session_content_history_session_id_fkey
    FOREIGN KEY (session_id) REFERENCES public.sessions(id) ON DELETE CASCADE;
END $$;
