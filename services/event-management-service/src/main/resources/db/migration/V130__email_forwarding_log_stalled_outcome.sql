-- #1139: a forward that stalled after sending began is recorded once as 'stalled', so later SQS
-- redeliveries of the same object see a finished row instead of re-reporting the stall.
ALTER TABLE email_forwarding_log DROP CONSTRAINT chk_email_forwarding_log_outcome;
ALTER TABLE email_forwarding_log ADD CONSTRAINT chk_email_forwarding_log_outcome CHECK (
    outcome IN ('processing', 'forwarded', 'partial', 'rejected', 'unresolved', 'failed', 'stalled')
);
