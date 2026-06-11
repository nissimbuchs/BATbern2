-- Story 7.5 rework — per-EVENT Q&A settings ("The Apéro Continues", reworked 2026-06-11).
--
-- Q&A configuration moves from a global default + per-session control to per-event settings,
-- edited in the organizer event Settings tab:
--   qna_enabled        -- master on/off for this event's Q&A
--   qna_open_trigger    -- WHEN the per-session windows open:
--                          'EVENT_COMPLETED'   (default) — the "digital afterglow" (Epic 7 NFR6)
--                          'SPEAKERS_PUBLISHED'          — opt-in: open once the speakers phase is
--                                                          published (a deliberate, organizer-chosen
--                                                          reversal of the afterglow guardrail)
--   qna_window_days     -- window length; windows close at (event date + this many days)
--
-- Per-session windows (session_qna_window) still hold per-session threads, but their lifecycle is
-- now driven entirely by these event settings + an event-level open/close control — there is no
-- per-session configuration anymore.

ALTER TABLE events
    ADD COLUMN qna_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN qna_open_trigger  VARCHAR(20) NOT NULL DEFAULT 'EVENT_COMPLETED',
    ADD COLUMN qna_window_days   INTEGER     NOT NULL DEFAULT 14;

ALTER TABLE events
    ADD CONSTRAINT chk_events_qna_open_trigger
        CHECK (qna_open_trigger IN ('EVENT_COMPLETED', 'SPEAKERS_PUBLISHED')),
    ADD CONSTRAINT chk_events_qna_window_days
        CHECK (qna_window_days BETWEEN 1 AND 365);
