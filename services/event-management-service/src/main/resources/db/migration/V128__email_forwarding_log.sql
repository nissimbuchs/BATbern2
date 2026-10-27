-- Email forwarding moves from the forwarder Lambda into EMS (GHSA-xjh8-6g65-m45r follow-up).
-- One row per inbound S3 object: SQS delivers at least once, and a redelivered
-- batbern{N}-participants@ mail must not reach every registrant twice.
-- Deliberately no addresses, subjects or bodies: the S3 key, the alias local parts and counts
-- are enough to operate the pipeline (PII minimisation).
CREATE TABLE email_forwarding_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    s3_key          VARCHAR(1024) NOT NULL,
    received_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    aliases         VARCHAR(1000),
    outcome         VARCHAR(20)   NOT NULL DEFAULT 'processing',
    recipient_count INTEGER       NOT NULL DEFAULT 0,
    failed_count    INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_email_forwarding_log_s3_key UNIQUE (s3_key),
    CONSTRAINT chk_email_forwarding_log_outcome CHECK (
        outcome IN ('processing', 'forwarded', 'partial', 'rejected', 'unresolved', 'failed')
    )
);

CREATE INDEX idx_email_forwarding_log_received_at ON email_forwarding_log (received_at);
