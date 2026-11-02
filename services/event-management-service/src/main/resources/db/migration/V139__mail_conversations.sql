-- Story 16.2a: speaker replies become conversations (Mail Center, Epic 16).
--
-- Inbound mail is stored as mail_message rows with direction 'IN', grouped into a
-- mail_conversation that carries status and assignee. The text needed for display is stored at
-- receipt (the raw MIME in S3 expires after 7 days). Automatic mail (out-of-office, bounces,
-- calendar replies, keyword actions, loops) is only counted, in mail_automatic_event, without any
-- address, subject or body.
-- Retention: same rule as mail_message (MailMessageRetentionScheduler): event date + 12 months,
-- without an event last activity + 12 months.

CREATE SEQUENCE mail_conversation_seq;

CREATE TABLE mail_conversation (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_key    VARCHAR(40)  NOT NULL,
    event_code          VARCHAR(50),
    audience            VARCHAR(20)  NOT NULL,
    subject             TEXT         NOT NULL,
    counterpart_address VARCHAR(320) NOT NULL,
    counterpart_name    VARCHAR(255),
    status              VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    assignee_username   VARCHAR(100),
    reply_token         VARCHAR(16),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(100),
    last_message_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_inbound_at     TIMESTAMPTZ,
    CONSTRAINT uq_mail_conversation_key UNIQUE (conversation_key),
    CONSTRAINT uq_mail_conversation_reply_token UNIQUE (reply_token),
    CONSTRAINT chk_mail_conversation_status CHECK (status IN ('OPEN', 'WAITING', 'DONE')),
    CONSTRAINT chk_mail_conversation_audience CHECK (audience IN
        ('NEWSLETTER', 'REGISTRANTS', 'SPEAKERS', 'VENUE', 'PARTNERS', 'ORGANIZERS', 'SYSTEM'))
);

CREATE INDEX idx_mail_conversation_event_status
    ON mail_conversation (event_code, status, last_message_at DESC);
CREATE INDEX idx_mail_conversation_counterpart ON mail_conversation (counterpart_address);

ALTER TABLE mail_message
    ADD COLUMN conversation_id   UUID REFERENCES mail_conversation (id) ON DELETE CASCADE,
    ADD COLUMN from_address      VARCHAR(320),
    ADD COLUMN from_name         VARCHAR(255),
    ADD COLUMN to_addresses      TEXT,
    ADD COLUMN message_id_header VARCHAR(998),
    ADD COLUMN in_reply_to       VARCHAR(998),
    ADD COLUMN references_header TEXT,
    ADD COLUMN body_text         TEXT,
    ADD COLUMN inbound_path      VARCHAR(10),
    ADD COLUMN alias_local_part  VARCHAR(64),
    ADD COLUMN attachments       JSONB,
    ADD CONSTRAINT chk_mail_message_inbound_path CHECK (inbound_path IS NULL OR inbound_path IN ('REPLIES', 'ALIAS'));

CREATE INDEX idx_mail_message_conversation ON mail_message (conversation_id);
CREATE INDEX idx_mail_message_message_id_header ON mail_message (message_id_header);

-- The Message-ID header as SES sent it, taken from the SES delivery event (Story 16.1b listener).
ALTER TABLE mail_recipient ADD COLUMN message_id_header VARCHAR(998);
CREATE INDEX idx_mail_recipient_message_id_header ON mail_recipient (message_id_header);

CREATE TABLE mail_automatic_event (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dedupe_key  VARCHAR(100) NOT NULL,
    event_code  VARCHAR(50),
    kind        VARCHAR(20)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_mail_automatic_event_dedupe UNIQUE (dedupe_key),
    CONSTRAINT chk_mail_automatic_event_kind CHECK (kind IN
        ('OUT_OF_OFFICE', 'BOUNCE', 'CALENDAR_ACCEPTED', 'CALENDAR_DECLINED', 'CALENDAR_TENTATIVE',
         'KEYWORD_ACCEPT', 'KEYWORD_CANCEL', 'UNSUBSCRIBE', 'LOOP'))
);

CREATE INDEX idx_mail_automatic_event_event_kind ON mail_automatic_event (event_code, kind);
CREATE INDEX idx_mail_automatic_event_received ON mail_automatic_event (received_at);
