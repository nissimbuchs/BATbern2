-- Story 16.1a: record every outbound mail, so the team sees what went out (Mail Center, Epic 16).
--
-- One mail_message per send group (one organizer request, one scheduler run x event x template,
-- or one transactional mail), one mail_recipient per address it went to. The newsletter keeps its
-- per-recipient status in newsletter_recipients and gets no mail_recipient rows (AC3).
-- Bodies are stored with credentials redacted (token links, registration code, temp password).
-- Retention: deleted once the event date and the mail are both older than 12 months
-- (MailMessageRetentionScheduler).

CREATE SEQUENCE mail_message_seq;

CREATE TABLE mail_message (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    mail_key           VARCHAR(80)  NOT NULL,
    send_group_id      VARCHAR(256) NOT NULL,
    event_code         VARCHAR(50),
    direction          VARCHAR(3)   NOT NULL DEFAULT 'OUT',
    audience           VARCHAR(20)  NOT NULL,
    template_key       VARCHAR(100),
    content_source     VARCHAR(10)  NOT NULL,
    subject            TEXT         NOT NULL,
    body_html          TEXT         NOT NULL,
    sender_username    VARCHAR(100),
    newsletter_send_id UUID         REFERENCES newsletter_sends (id) ON DELETE SET NULL,
    recipient_count    INTEGER      NOT NULL DEFAULT 0,
    is_test            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_mail_message_mail_key UNIQUE (mail_key),
    CONSTRAINT uq_mail_message_send_group UNIQUE (send_group_id),
    CONSTRAINT chk_mail_message_direction CHECK (direction IN ('OUT', 'IN')),
    CONSTRAINT chk_mail_message_audience CHECK (audience IN
        ('NEWSLETTER', 'REGISTRANTS', 'SPEAKERS', 'VENUE', 'PARTNERS', 'ORGANIZERS', 'SYSTEM')),
    CONSTRAINT chk_mail_message_content_source CHECK (content_source IN ('TEMPLATE', 'OWN_TEXT'))
);

CREATE INDEX idx_mail_message_event_created ON mail_message (event_code, created_at DESC);
CREATE INDEX idx_mail_message_created ON mail_message (created_at);

CREATE TABLE mail_recipient (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    mail_message_id UUID         NOT NULL REFERENCES mail_message (id) ON DELETE CASCADE,
    address         VARCHAR(320) NOT NULL,
    kind            VARCHAR(2)   NOT NULL,
    ses_message_id  VARCHAR(255),
    status          VARCHAR(10)  NOT NULL,
    failure_reason  VARCHAR(500),
    body_html       TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_mail_recipient_kind CHECK (kind IN ('TO', 'CC')),
    CONSTRAINT chk_mail_recipient_status CHECK (status IN ('SENT', 'DELIVERED', 'FAILED'))
);

CREATE INDEX idx_mail_recipient_message ON mail_recipient (mail_message_id);
CREATE INDEX idx_mail_recipient_ses_message ON mail_recipient (ses_message_id);
