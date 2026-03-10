-- Story 10.7 robustness: add send-job status tracking to newsletter_sends
-- Fixes critical thread-pool overflow bug; enables progress polling + retry.
ALTER TABLE newsletter_sends
    ADD COLUMN status        VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN sent_count    INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN failed_count  INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN started_at    TIMESTAMPTZ,
    ADD COLUMN completed_at  TIMESTAMPTZ;

-- Backfill: treat every pre-existing row as a completed send.
UPDATE newsletter_sends
SET status       = 'COMPLETED',
    started_at   = sent_at,
    completed_at = sent_at,
    sent_count   = COALESCE(recipient_count, 0);

-- Index for duplicate-send prevention: quickly check IN_PROGRESS per event.
CREATE INDEX idx_newsletter_sends_event_status
    ON newsletter_sends (event_id, status);
