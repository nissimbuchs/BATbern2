-- V125__newsletter_subscriber_email_case_insensitive_unique.sql
-- One newsletter subscriber per address regardless of capitalisation (#1099). Production had a
-- person subscribed twice ("x@…" and "X@…") and receiving every newsletter twice; that pair was
-- merged by hand on 2026-10-18 and the service now stores addresses lower-cased and matches them
-- case-insensitively. This index makes the rule hold at the database level.
-- Fails (and keeps the old image running) if case-duplicates exist again; check with:
--   SELECT lower(email), count(*) FROM newsletter_subscribers GROUP BY 1 HAVING count(*) > 1;
CREATE UNIQUE INDEX uq_newsletter_subscribers_email_lower ON newsletter_subscribers (lower(email));
