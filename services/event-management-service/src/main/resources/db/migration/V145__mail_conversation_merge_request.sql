-- V145__mail_conversation_merge_request.sql
-- #1237: a profile merge request (company-user-management, profile_merge_requests) is decided by an
-- organizer in the Mail Center. Its conversation carries the request id, so the Mail Center shows
-- the request with approve / decline. No foreign key: the request lives in another service (ADR-003).
ALTER TABLE mail_conversation ADD COLUMN merge_request_id UUID;
CREATE UNIQUE INDEX uq_mail_conversation_merge_request ON mail_conversation (merge_request_id)
    WHERE merge_request_id IS NOT NULL;
