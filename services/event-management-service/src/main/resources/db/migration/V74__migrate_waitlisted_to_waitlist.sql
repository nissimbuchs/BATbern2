-- Story 10.11 (CR fix M3): Migrate any legacy 'waitlisted' rows to 'waitlist'
-- V73 added 'waitlist' status but kept 'waitlisted' in the constraint for backwards compat.
-- This migration normalises any pre-existing rows and removes the obsolete value.

-- Normalise any legacy rows (safe no-op if none exist)
UPDATE registrations
SET waitlist_position = COALESCE(waitlist_position, 1)
WHERE status = 'waitlisted';

UPDATE registrations
SET status = 'waitlist'
WHERE status = 'waitlisted';

-- Drop old constraint and re-add without 'waitlisted'
ALTER TABLE registrations DROP CONSTRAINT IF EXISTS registrations_status_check;
ALTER TABLE registrations ADD CONSTRAINT registrations_status_check
    CHECK (status IN ('registered', 'waitlist', 'confirmed', 'cancelled', 'attended'));
