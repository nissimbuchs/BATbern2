-- Story 10.7: Newsletter Subscription & Sending
-- Creates newsletter_subscribers, newsletter_sends, newsletter_recipients tables

CREATE TABLE newsletter_subscribers (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL,
    first_name        VARCHAR(100),
    language          VARCHAR(5)   NOT NULL DEFAULT 'de',
    source            VARCHAR(50)  NOT NULL DEFAULT 'explicit',
    username          VARCHAR(100),
    unsubscribe_token VARCHAR(255) NOT NULL,
    subscribed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    unsubscribed_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_newsletter_email           UNIQUE (email),
    CONSTRAINT uq_newsletter_unsubscribe_tok UNIQUE (unsubscribe_token)
);

CREATE TABLE newsletter_sends (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         UUID         NOT NULL REFERENCES events(id),
    template_key     VARCHAR(100) NOT NULL,
    is_reminder      BOOLEAN      NOT NULL DEFAULT FALSE,
    locale           VARCHAR(5)   NOT NULL DEFAULT 'de',
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_by_username VARCHAR(100) NOT NULL,
    recipient_count  INTEGER,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE newsletter_recipients (
    send_id         UUID         NOT NULL REFERENCES newsletter_sends(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    delivery_status VARCHAR(50)  NOT NULL DEFAULT 'sent',
    PRIMARY KEY (send_id, email)
);
