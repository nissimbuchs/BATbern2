-- V146__mail_conversation_logo_request.sql
-- #1246: a company logo request (company-user-management, company_logo_requests) is decided by an
-- organizer in the Mail Center. Its conversation carries the request id, so the Mail Center shows
-- the old and the new logo with accept / decline. No foreign key: the request lives in another
-- service (ADR-003).
ALTER TABLE mail_conversation ADD COLUMN logo_request_id UUID;
CREATE UNIQUE INDEX uq_mail_conversation_logo_request ON mail_conversation (logo_request_id)
    WHERE logo_request_id IS NOT NULL;
