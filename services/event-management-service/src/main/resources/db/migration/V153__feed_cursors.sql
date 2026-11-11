-- V153__feed_cursors.sql
-- Epic 17 entry 7 (ADR-016 decision 4): event-management pulls upstream changes from /internal
-- feeds instead of being called. Each feed's position is stored here, one row per feed, written
-- after every page the job has processed. The cursor is the provider's opaque value.
-- First user: 'cums.mail-center-requests' (MailCenterRequestFeedJob); entry 8b reuses the table.
CREATE TABLE feed_cursors (
    feed       VARCHAR(100) PRIMARY KEY,
    cursor     TEXT         NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);
