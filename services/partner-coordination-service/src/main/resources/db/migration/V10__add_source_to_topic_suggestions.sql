-- V10__add_source_to_topic_suggestions.sql
-- Story 7.1: "Topics From the Floor" — logged-in attendees suggest topics into the
-- existing topic pool, tagged source='COMMUNITY' to distinguish them from partner-sourced
-- ('PARTNER') suggestions. Casing matches the existing status column (UPPER_CASE in DB,
-- @Enumerated(EnumType.STRING) in Java).
--
-- Community suggestions have no partner company, so company_name is relaxed to nullable.

ALTER TABLE topic_suggestions
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'PARTNER';

ALTER TABLE topic_suggestions
    ADD CONSTRAINT chk_topic_source CHECK (source IN ('PARTNER', 'COMMUNITY'));

-- Community (attendee) suggestions are not attached to a partner company.
ALTER TABLE topic_suggestions
    ALTER COLUMN company_name DROP NOT NULL;

CREATE INDEX idx_topic_suggestions_source ON topic_suggestions(source);
