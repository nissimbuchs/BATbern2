-- V159 (#1260): a session material is published after the event only when it is marked public.
-- Speakers upload their PowerPoint for the moderator next to a PDF for the public; only the public
-- ones are shown. Existing rows: a PDF (by extension or MIME type) stays public, every other type
-- becomes internal and disappears from the public archive. The NOTICEs list the non-PDF rows per
-- event, so the deploy can name them. A row inserted without the flag is internal (DEFAULT false).
-- The updated_at trigger is off during the backfill: setting the flag is not an edit of the material,
-- and its last-changed time must survive the deploy.
ALTER TABLE session_materials ADD COLUMN publicly_visible BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE session_materials DISABLE TRIGGER update_session_materials_updated_at;

UPDATE session_materials
SET publicly_visible = true
WHERE lower(file_extension) = 'pdf' OR lower(mime_type) = 'application/pdf';

ALTER TABLE session_materials ENABLE TRIGGER update_session_materials_updated_at;

DO $$
DECLARE
    r RECORD;
    total INTEGER := 0;
BEGIN
    FOR r IN
        SELECT s.event_code, count(*) AS internal_count
        FROM session_materials m
        JOIN sessions s ON s.id = m.session_id
        WHERE NOT m.publicly_visible
        GROUP BY s.event_code
        ORDER BY s.event_code
    LOOP
        RAISE NOTICE 'V159: event % has % non-PDF material(s), now not public', r.event_code, r.internal_count;
        total := total + r.internal_count;
    END LOOP;
    RAISE NOTICE 'V159: % non-PDF material(s) in total, now not public', total;
END $$;
