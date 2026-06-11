-- Story 7.4 "Thank the Organizers" — one-click post-event gratitude for the volunteer organizers.
--
-- Any attendee (logged-in OR anonymous) can thank the organizers after an event goes
-- live/completed. Two submission shapes share one table:
--   * Logged-in: deduped to ONE row per (event, username); a repeat updates the note,
--     it never double-counts. Enforced by the partial unique index below.
--   * Anonymous: a clap-style row with thanked_by_username = NULL (NOT user-deduped),
--     guarded by Turnstile + a per-(event,IP) rate limit at the service layer.
--
-- The aggregate count is public; free-text notes are organizer-visible only (AC6) — there is
-- no public note wall and no approval queue, so notes are just stored raw and never exposed
-- on the public GET.
--
-- Columns:
--   event_id              -- in-service UUID FK to events (same service → UUID FK allowed, ADR-003)
--   thanked_by_username    -- meaningful ID of the logged-in attendee (ADR-003); NULL for anonymous claps
--   note                   -- optional short free-text, organizer-visible only
--   created_at             -- submission timestamp

CREATE TABLE organizer_thanks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    thanked_by_username VARCHAR(100),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Logged-in dedupe (AC4): one thank-you per attendee per event. Partial so it only constrains
-- logged-in rows — anonymous claps (username NULL) are unaffected and not user-deduped (AC5).
CREATE UNIQUE INDEX ux_organizer_thanks_user
    ON organizer_thanks (event_id, thanked_by_username)
    WHERE thanked_by_username IS NOT NULL;

-- Aggregate count + notes are always read per-event.
CREATE INDEX ix_organizer_thanks_event ON organizer_thanks (event_id);
