-- #1244 (owner decision 2026-11-07): event mails go to the primary address; a copy only to
-- confirmed additional addresses that opted in. Until now every additional address got a CC,
-- confirmed or not. Default off, also for existing rows.
ALTER TABLE user_additional_emails
    ADD COLUMN receive_event_mail BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN user_additional_emails.receive_event_mail IS
    '#1244: copy of event mails (registration, invitations, reminders) to this address; only honoured when verified_at is set';
