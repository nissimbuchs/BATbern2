-- Story 10.29 AC3: Bounce tracking fields for newsletter list hygiene
--
-- Adds bounce/complaint tracking to newsletter_subscribers and newsletter_recipients
-- to support automatic suppression of hard-bouncing and complaining addresses.

-- Newsletter subscribers: bounce tracking + suppression
ALTER TABLE newsletter_subscribers
    ADD COLUMN bounce_type VARCHAR(20),
    ADD COLUMN bounce_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_bounced_at TIMESTAMPTZ,
    ADD COLUMN suppressed_at TIMESTAMPTZ;

-- Partial index for efficient suppressed-subscriber lookups
CREATE INDEX idx_newsletter_subscribers_suppressed
    ON newsletter_subscribers (suppressed_at)
    WHERE suppressed_at IS NOT NULL;

-- Newsletter recipients: per-send bounce tracking
ALTER TABLE newsletter_recipients
    ADD COLUMN bounce_type VARCHAR(20),
    ADD COLUMN bounced_at TIMESTAMPTZ;
