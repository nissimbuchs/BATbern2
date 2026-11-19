-- V158 (#1288, owner option 1): double opt-in for the newsletter. A subscription counts as active
-- only once its owner confirmed it: confirmed_at is set. An anonymous subscribe leaves it pending
-- (confirmed_at NULL) and stores a single-use confirmation_token, which the link in the
-- confirmation mail carries. confirmation_sent_at holds when that mail last went out: at most one
-- per address and hour, so repeated anonymous requests cannot flood an address. Every existing row
-- predates the double opt-in and keeps receiving the newsletter: it counts as confirmed at its
-- subscribed_at.
ALTER TABLE newsletter_subscribers
    ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN confirmation_token VARCHAR(255),
    ADD COLUMN confirmation_sent_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX uq_newsletter_subscribers_confirmation_token
    ON newsletter_subscribers (confirmation_token);

UPDATE newsletter_subscribers SET confirmed_at = subscribed_at;
