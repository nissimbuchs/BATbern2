-- Story 16.9: what a recorded mail is about beyond its event, e.g. 'partner-meeting:{meetingId}' for a
-- partner meeting invite or cancellation recorded by partner-coordination through POST /internal/mails.
-- A meaningful id by value, never a foreign key across services (ADR-003). Null for every other mail.

ALTER TABLE mail_message ADD COLUMN reference VARCHAR(100);

CREATE INDEX idx_mail_message_event_reference ON mail_message (event_code, reference)
    WHERE reference IS NOT NULL;
