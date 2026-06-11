-- Story 7.2 "I Could Speak on That" — attendee speaker self-nomination (ADR-012).
--
-- A self-nomination is an *application / pitch*, distinct from the speaker (workflow) and the
-- session (content). Per ADR-012 / Epic 11.E.8, `speaker_pool` is the workflow state machine
-- ONLY — content (title/abstract/materials) never lives there (V103 dropped username/email for
-- the same reason). So this migration:
--   1. adds a single provenance flag `source` to speaker_pool (NOT content), and
--   2. creates a dedicated `session_proposals` table for the proposed talk.
--
-- The pitch stays in `session_proposals` while the candidate is triaged (IDENTIFIED → CONTACTED).
-- At promote-to-READY the hook seeds the canonical session (sessions.title + the first
-- session_content_history row) FROM the proposal; the proposal row then becomes immutable audit.
-- `session_proposals` has NO status column — acceptance/rejection is read from the linked
-- speaker_pool workflow state (single source of workflow truth, ADR-009). A schema-fitness test
-- asserts speaker_pool never gains title/abstract/materials content columns.
--
-- NOTE (dev-only Flyway exception): this file replaces an earlier V109 that added proposed_*
-- content columns directly to speaker_pool. That earlier V109 was applied to ONE dev database
-- only (branch feat/epic-7-attendee-contribution is unpushed; staging/prod never saw it), so it
-- was reconciled on dev via a manual schema fix + `flywayRepair`. Once this branch is pushed,
-- this file is frozen forever per the standard Flyway rule.

ALTER TABLE speaker_pool
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'organizer_added';

-- Keep the set of valid sources explicit + extendable (future: 'partner_referral', etc.).
ALTER TABLE speaker_pool
    ADD CONSTRAINT chk_speaker_pool_source
        CHECK (source IN ('organizer_added', 'self_nomination'));

CREATE TABLE session_proposals (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_pool_id      UUID NOT NULL REFERENCES speaker_pool(id) ON DELETE CASCADE,
    event_id             UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    proposed_by_username VARCHAR(100) NOT NULL,   -- meaningful ID (ADR-003), who pitched
    proposed_title       VARCHAR(200) NOT NULL,   -- aligned to session_content_history.title (200)
    proposed_abstract    TEXT         NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- One self-nomination per attendee per event (AC8′). Full unique (not partial) — this table
    -- holds only self-nomination pitches, so every row is constrained.
    CONSTRAINT ux_session_proposals_event_user UNIQUE (event_id, proposed_by_username)
);

-- The promote hook + organizer kanban look the proposal up by the pool row it belongs to.
CREATE INDEX idx_session_proposals_speaker_pool ON session_proposals (speaker_pool_id);
