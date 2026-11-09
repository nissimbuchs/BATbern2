-- A registrant who did not come to the event. Not an attendee: analytics count confirmed and
-- attended only, so a no-show must not stay 'confirmed' after the event.
ALTER TABLE registrations DROP CONSTRAINT IF EXISTS registrations_status_check;
ALTER TABLE registrations ADD CONSTRAINT registrations_status_check
    CHECK (status IN ('registered', 'waitlist', 'confirmed', 'cancelled', 'attended', 'no_show'));

-- Own text to registrants can go to chosen statuses only (e.g. the no-shows). The async send
-- re-resolves the audience from this row, so the choice is stored with it. NULL = the default
-- audience (registered, confirmed, attended).
ALTER TABLE newsletter_sends ADD COLUMN registrant_statuses VARCHAR(100);
