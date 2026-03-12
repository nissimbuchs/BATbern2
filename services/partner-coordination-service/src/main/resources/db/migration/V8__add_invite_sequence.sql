-- V8__add_invite_sequence.sql
-- Story 8.3 enhancement: track ICS SEQUENCE number for calendar update recognition.
--
-- RFC 5545 §3.8.7.4: SEQUENCE must increment on every resend of a METHOD:REQUEST
-- so that Outlook, Gmail, and macOS Mail recognise the resend as an update of the
-- existing calendar entry rather than a new duplicate event.

ALTER TABLE partner_meetings
    ADD COLUMN IF NOT EXISTS invite_sequence INTEGER NOT NULL DEFAULT 0;
