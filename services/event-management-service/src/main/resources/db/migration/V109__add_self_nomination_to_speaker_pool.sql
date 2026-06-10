-- Story 7.2 "I Could Speak on That" — attendee speaker self-nomination.
--
-- Logged-in attendees raise their hand with a proposed talk once an event's topic is
-- set + published. A self-nomination is a regular speaker_pool row entering at the
-- IDENTIFIED default (the add-to-pool save path), tagged so organizers can tell it
-- apart from organizer-sourced candidates and route it through the unchanged
-- triage -> promote flow (Epic 11 / ADR-009). NO Cognito user / SPEAKER role /
-- session_users row is created here — provisioning still happens only at promote-to-READY.
--
-- Columns:
--   source                 -- 'organizer_added' (default, existing rows) | 'self_nomination'
--   proposed_by_username    -- JWT username of the self-nominating attendee (meaningful ID, ADR-003)
--   proposed_session_title  -- the talk title the attendee proposed (organizer-visible)
--   proposed_abstract       -- the talk abstract, stored raw (no agent pre-screen, AC3)

ALTER TABLE speaker_pool
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'organizer_added',
    ADD COLUMN proposed_by_username VARCHAR(100),
    ADD COLUMN proposed_session_title VARCHAR(255),
    ADD COLUMN proposed_abstract TEXT;

-- Keep the set of valid sources explicit + extendable (future: 'partner_referral', etc.).
ALTER TABLE speaker_pool
    ADD CONSTRAINT chk_speaker_pool_source
        CHECK (source IN ('organizer_added', 'self_nomination'));

-- One self-nomination per attendee per event (AC8). Partial unique index so it only
-- constrains self-nomination rows — organizer-added rows are unaffected and an attendee
-- can still self-nominate for different events.
CREATE UNIQUE INDEX ux_speaker_pool_self_nom
    ON speaker_pool (event_id, proposed_by_username)
    WHERE source = 'self_nomination';
