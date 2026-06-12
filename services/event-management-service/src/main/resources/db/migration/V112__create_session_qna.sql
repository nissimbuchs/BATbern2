-- Story 7.5 "The Apéro Continues" — per-session, time-boxed Q&A (the digital afterglow).
--
-- When an event reaches EVENT_COMPLETED, a Q&A window opens for EACH of its sessions with a
-- default close 14 days out. Logged-in attendees post questions/answers during the open window;
-- a scheduled ShedLock job freezes windows to read-only once closes_at passes, and the frozen
-- thread stays attached to the session's (eventually archived) page. Organizers can extend,
-- close early, and take down posts (soft-delete tombstone).
--
-- Status enum (DB lowercase ↔ Java UPPER via QnaWindowStatusConverter): 'open' | 'frozen'.

CREATE TABLE session_qna_window (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- In-service UUID FK to sessions (same service → UUID FK allowed, ADR-003).
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    -- Denormalized public event id for convenient lookup / archive routing (ADR-003 meaningful id).
    event_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    opens_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closes_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One Q&A window per session — makes window-opening idempotent (AC1).
    CONSTRAINT ux_session_qna_window_session UNIQUE (session_id),
    CONSTRAINT chk_session_qna_window_status CHECK (status IN ('open', 'frozen'))
);

CREATE TABLE session_qna_post (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    window_id UUID NOT NULL REFERENCES session_qna_window(id) ON DELETE CASCADE,
    -- Self-FK for one-level threading (question → answer). NULL = top-level question.
    parent_post_id UUID REFERENCES session_qna_post(id) ON DELETE CASCADE,
    -- Meaningful ID (ADR-003) of the logged-in poster; posts are always attributed.
    posted_by_username VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    -- Soft-delete tombstone (Resolved Decision #3): set on organizer takedown, never hard-deleted.
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_session_qna_window_event ON session_qna_window (event_code);
-- The freeze job scans for open windows whose close time has passed.
CREATE INDEX ix_session_qna_window_freeze ON session_qna_window (status, closes_at);
CREATE INDEX ix_session_qna_post_window ON session_qna_post (window_id, created_at);
