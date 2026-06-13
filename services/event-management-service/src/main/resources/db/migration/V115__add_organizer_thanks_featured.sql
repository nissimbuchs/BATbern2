-- Story 7.7 "Curated Thank-You Notes in the Partner Marquee" — extends Story 7.4.
--
-- An organizer can FEATURE individual logged-in thank-you notes. Featured notes surface on the
-- PUBLIC partner marquee (intermingled with partner logos), enriched with the author's first
-- name + company logo.
--
-- A row is "featured" when featured_at IS NOT NULL. Only LOGGED-IN notes
-- (thanked_by_username IS NOT NULL) are featurable — anonymous claps have no name and can never
-- be featured, which preserves Story 7.4 AC6's anti-troll guarantee on the public surface.
--
-- NOTE: V112–V114 are the Story 7.5 Q&A migrations; this is the next free number after V114.
-- V111 (the organizer_thanks table) is already shipped and MUST NOT be edited (Flyway checksum).

ALTER TABLE organizer_thanks ADD COLUMN featured_at TIMESTAMPTZ;

-- Drives the public random-featured query: a tiny candidate set of featured + logged-in rows.
CREATE INDEX ix_organizer_thanks_featured
    ON organizer_thanks (featured_at)
    WHERE featured_at IS NOT NULL AND thanked_by_username IS NOT NULL;
