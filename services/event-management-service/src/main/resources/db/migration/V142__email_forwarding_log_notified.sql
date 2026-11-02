-- Story 16.6 AC8: with a group switched to the Mail Center, the recipients get a notification
-- instead of the forwarded copy. Recorded as its own outcome so the log stays honest.
ALTER TABLE email_forwarding_log DROP CONSTRAINT chk_email_forwarding_log_outcome;
ALTER TABLE email_forwarding_log ADD CONSTRAINT chk_email_forwarding_log_outcome CHECK (
    outcome IN ('processing', 'forwarded', 'partial', 'rejected', 'unresolved', 'failed', 'stalled',
                'dropped_auto_reply', 'dropped_bounce', 'dropped_loop', 'dropped_malformed', 'notified')
);
