-- Story 10.2: Email Template Management
-- Creates DB-backed email_templates table for organizer-managed templates

CREATE TABLE email_templates (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key  VARCHAR(100) NOT NULL,
    category      VARCHAR(50)  NOT NULL,
    locale        VARCHAR(5)   NOT NULL,
    subject       VARCHAR(500),
    html_body     TEXT         NOT NULL,
    variables     JSONB,
    is_layout     BOOLEAN      NOT NULL DEFAULT FALSE,
    layout_key    VARCHAR(100),
    is_system_template BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT email_templates_key_locale_unique UNIQUE (template_key, locale)
);

COMMENT ON TABLE email_templates IS 'DB-backed email templates — seeded from classpath on startup, editable by organizers';
COMMENT ON COLUMN email_templates.template_key IS 'Identifier key (e.g. speaker-invitation, batbern-default)';
COMMENT ON COLUMN email_templates.category IS 'SPEAKER | REGISTRATION | TASK_REMINDER | LAYOUT';
COMMENT ON COLUMN email_templates.is_layout IS 'True = layout (HTML shell) template; false = content template';
COMMENT ON COLUMN email_templates.layout_key IS 'NULL = standalone; non-null = uses this layout key at send time';
COMMENT ON COLUMN email_templates.is_system_template IS 'System templates cannot be deleted via API';
