-- V159 (#1260): a session material is published after the event only when it is marked public.
-- Speakers upload their PowerPoint for the moderator next to a PDF for the public; only the public
-- ones are shown. Existing rows: a PDF (by extension or MIME type) stays public, every other type
-- becomes internal and disappears from the public archive. The NOTICEs list the non-PDF rows per
-- event, so the deploy can name them. A row inserted without the flag is internal (DEFAULT false).
ALTER TABLE session_materials ADD COLUMN publicly_visible BOOLEAN;

UPDATE session_materials
SET publicly_visible = (lower(file_extension) = 'pdf' OR lower(mime_type) = 'application/pdf');

ALTER TABLE session_materials
    ALTER COLUMN publicly_visible SET NOT NULL,
    ALTER COLUMN publicly_visible SET DEFAULT false;

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
