-- Story 16.0 (#1161): a stable, per-registration UID for the calendar invite in the
-- "registration confirmed" mail, so that a calendar "Declined" reply (iMIP METHOD:REPLY) can be
-- matched to its registration. Deliberately NOT the registration code: that code is a read
-- credential (GET /events/*/registrations/{code}), and a UID travels with every forwarded or
-- shared calendar entry. A random UUID identifies the registration and grants nothing.
-- Existing rows get a value from the default; invites already sent keep their random UIDs and are
-- matched by sender + event date instead.
ALTER TABLE registrations ADD COLUMN calendar_uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX uq_registrations_calendar_uid ON registrations (calendar_uid);
