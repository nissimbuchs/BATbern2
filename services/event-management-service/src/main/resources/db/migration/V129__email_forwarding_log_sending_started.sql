-- Separates "claimed" from "sending began" in email_forwarding_log (follow-up to V128).
-- A claim with no sending_started_at whose task died (Fargate Spot, deploy, OOM) may be taken
-- over by the SQS redelivery: nothing went out, so a retry cannot double-send. A claim that
-- started sending and never completed is not retried, but reported (EmailsFailed alarm).
ALTER TABLE email_forwarding_log ADD COLUMN sending_started_at TIMESTAMPTZ;
