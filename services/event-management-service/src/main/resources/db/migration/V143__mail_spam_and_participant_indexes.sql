-- Story 16.7 AC3/AC4: SES spam and virus verdicts. A spam-FAIL mail is stored as an inbound
-- message flagged spam (Spam list, no conversation, deleted after 30 days); a virus-FAIL mail is
-- only counted. Neither is forwarded any more; the replies@ path records that in the forwarding log.
ALTER TABLE mail_message ADD COLUMN is_spam BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_mail_message_spam ON mail_message (created_at) WHERE is_spam;

ALTER TABLE mail_automatic_event DROP CONSTRAINT chk_mail_automatic_event_kind;
ALTER TABLE mail_automatic_event ADD CONSTRAINT chk_mail_automatic_event_kind CHECK (kind IN
    ('OUT_OF_OFFICE', 'BOUNCE', 'CALENDAR_ACCEPTED', 'CALENDAR_DECLINED', 'CALENDAR_TENTATIVE',
     'KEYWORD_ACCEPT', 'KEYWORD_CANCEL', 'UNSUBSCRIBE', 'LOOP', 'SPAM', 'VIRUS'));

ALTER TABLE email_forwarding_log DROP CONSTRAINT chk_email_forwarding_log_outcome;
ALTER TABLE email_forwarding_log ADD CONSTRAINT chk_email_forwarding_log_outcome CHECK (
    outcome IN ('processing', 'forwarded', 'partial', 'rejected', 'unresolved', 'failed', 'stalled',
                'dropped_auto_reply', 'dropped_bounce', 'dropped_loop', 'dropped_malformed', 'notified',
                'dropped_spam', 'dropped_virus')
);

-- Story 16.10: the mail of one person or partner is matched on their addresses at read time.
CREATE INDEX idx_mail_recipient_address_lower ON mail_recipient (lower(address));
CREATE INDEX idx_mail_message_from_address_lower ON mail_message (lower(from_address)) WHERE from_address IS NOT NULL;
