-- Story 16.0 (#1160): replies@ mail that matches no keyword is forwarded to the organizers instead
-- of being discarded. Mail that is deliberately NOT forwarded (automatic replies, bounces, loops,
-- malformed) is recorded too, so the log shows every inbound reply and what happened to it.
ALTER TABLE email_forwarding_log DROP CONSTRAINT chk_email_forwarding_log_outcome;
ALTER TABLE email_forwarding_log ADD CONSTRAINT chk_email_forwarding_log_outcome CHECK (
    outcome IN ('processing', 'forwarded', 'partial', 'rejected', 'unresolved', 'failed', 'stalled',
                'dropped_auto_reply', 'dropped_bounce', 'dropped_loop', 'dropped_malformed')
);
