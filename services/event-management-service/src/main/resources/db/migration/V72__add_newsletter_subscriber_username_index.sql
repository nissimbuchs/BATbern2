-- Story 10.7 CR fix: add index on newsletter_subscribers.username
-- for getMySubscription() / patchMySubscription() username lookups.
CREATE INDEX IF NOT EXISTS idx_newsletter_subscribers_username
    ON newsletter_subscribers (username)
    WHERE username IS NOT NULL;
