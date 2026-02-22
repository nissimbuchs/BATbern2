-- V4__update_topic_tables_for_story_8_2.sql
-- Story 8.2: Topic Suggestions & Voting
-- Replaces the V2 topic tables with a simpler schema aligned to AC1-6.
-- Changes from V2:
--   topic_suggestions: uses company_name+suggested_by (ADR-003) instead of partner_id FK;
--                      simpler status lifecycle (PROPOSED/SELECTED/DECLINED);
--                      adds planned_event column; drops business_justification.
--   topic_votes:       composite PK (topic_id, company_name) — toggle-on = row exists,
--                      toggle-off = row deleted; drops vote_weight and vote_value.

-- Drop old tables (no production data — feature was never deployed)
DROP TABLE IF EXISTS topic_votes CASCADE;
DROP TABLE IF EXISTS topic_suggestions CASCADE;

-- topic_suggestions: one row per suggested topic
CREATE TABLE topic_suggestions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name  VARCHAR(255) NOT NULL,        -- ADR-003: meaningful company identifier
    suggested_by  VARCHAR(100) NOT NULL,         -- username of the submitter
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PROPOSED',  -- PROPOSED | SELECTED | DECLINED
    planned_event VARCHAR(100),                  -- e.g. "BATbern58", organizer fills in when selecting
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT chk_topic_status CHECK (status IN ('PROPOSED', 'SELECTED', 'DECLINED'))
);

-- topic_votes: one row per partner-company per topic
-- toggle on  = INSERT; toggle off = DELETE
-- Duplicate vote = row already exists → ignore (idempotent)
CREATE TABLE topic_votes (
    topic_id      UUID         NOT NULL REFERENCES topic_suggestions(id) ON DELETE CASCADE,
    company_name  VARCHAR(255) NOT NULL,         -- ADR-003: meaningful company identifier
    voted_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (topic_id, company_name)         -- one vote per partner company per topic
);

CREATE INDEX idx_topic_suggestions_status ON topic_suggestions(status);
CREATE INDEX idx_topic_votes_topic        ON topic_votes(topic_id);
