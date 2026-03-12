-- V88: Create app_settings table for key-value application settings (Story 10.26)
-- Used for configurable forwarding recipients (e.g., support@batbern.ch contacts)

CREATE TABLE app_settings (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key   VARCHAR(255) NOT NULL UNIQUE,
    setting_value TEXT,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by    VARCHAR(100)
);

CREATE INDEX idx_app_settings_key ON app_settings (setting_key);
