-- V4__Create_user_profiles_table.sql
-- Story 1.16.2: User profiles with dual-identifier pattern
-- UUID PK (internal database key), username (public API ID)
-- Based on: docs/stories/1.14-2.user-management-service-foundation.md

-- Story 1.16.2: User profiles with dual-identifier pattern
-- UUID PK (internal), username (public API ID)
CREATE TABLE user_profiles (
    -- Internal database identifier (NOT exposed in API)
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Story 1.16.2: Public meaningful identifier (exposed in API as "id")
    username VARCHAR(100) NOT NULL UNIQUE,

    -- Authentication
    cognito_user_id VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,

    -- Profile information
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    bio TEXT,

    -- Story 1.16.2: Company reference uses company name (not UUID)
    company_id VARCHAR(12),  -- e.g., "GoogleZH", max 12 chars

    -- Profile picture
    profile_picture_url VARCHAR(2048),
    profile_picture_s3_key VARCHAR(500),

    -- Embedded preferences (UserPreferences @Embeddable)
    pref_theme VARCHAR(10) DEFAULT 'auto' CHECK (pref_theme IN ('light', 'dark', 'auto')),
    pref_language VARCHAR(2) DEFAULT 'de' CHECK (pref_language IN ('de', 'en', 'fr', 'it')),
    pref_email_notifications BOOLEAN DEFAULT TRUE,
    pref_in_app_notifications BOOLEAN DEFAULT TRUE,
    pref_push_notifications BOOLEAN DEFAULT FALSE,
    pref_notification_frequency VARCHAR(20) DEFAULT 'immediate' CHECK (pref_notification_frequency IN ('immediate', 'daily_digest', 'weekly_digest')),
    pref_quiet_hours_start TIME,
    pref_quiet_hours_end TIME,

    -- Embedded settings (UserSettings @Embeddable)
    settings_profile_visibility VARCHAR(20) DEFAULT 'public' CHECK (settings_profile_visibility IN ('public', 'members_only', 'private')),
    settings_show_email BOOLEAN DEFAULT FALSE,
    settings_show_company BOOLEAN DEFAULT TRUE,
    settings_show_activity_history BOOLEAN DEFAULT TRUE,
    settings_allow_messaging BOOLEAN DEFAULT TRUE,
    settings_allow_calendar_sync BOOLEAN DEFAULT FALSE,
    settings_timezone VARCHAR(50) DEFAULT 'Europe/Zurich',
    settings_two_factor_enabled BOOLEAN DEFAULT FALSE,

    -- Status
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE
);

-- Story 1.16.2: Username format validation (lowercase letters, dots, optional numeric suffix)
ALTER TABLE user_profiles ADD CONSTRAINT chk_username_format
    CHECK (username ~ '^[a-z]+\.[a-z]+(\.[0-9]+)?$');

-- Story 1.16.2: Company name format validation (alphanumeric, max 12 chars)
ALTER TABLE user_profiles ADD CONSTRAINT chk_company_id_format
    CHECK (company_id IS NULL OR (company_id ~ '^[a-zA-Z0-9]{1,12}$'));

-- Comments for clarity
COMMENT ON TABLE user_profiles IS 'User profiles with Story 1.16.2 dual-identifier pattern: UUID (internal PK) + username (public API ID)';
COMMENT ON COLUMN user_profiles.id IS 'Internal UUID primary key (NOT exposed in API)';
COMMENT ON COLUMN user_profiles.username IS 'Story 1.16.2: Public meaningful identifier (e.g., john.doe) - exposed as "id" in API responses';
COMMENT ON COLUMN user_profiles.company_id IS 'Story 1.16.2: Company name reference (e.g., GoogleZH), not UUID';
COMMENT ON COLUMN user_profiles.cognito_user_id IS 'AWS Cognito user identifier for authentication';
COMMENT ON COLUMN user_profiles.email IS 'User email address (unique, used for login)';
COMMENT ON COLUMN user_profiles.pref_theme IS 'UI theme preference (light, dark, auto)';
COMMENT ON COLUMN user_profiles.pref_language IS 'Preferred language (de, en, fr, it)';
COMMENT ON COLUMN user_profiles.pref_notification_frequency IS 'Notification delivery frequency';
COMMENT ON COLUMN user_profiles.settings_profile_visibility IS 'Profile visibility level (public, members_only, private)';
COMMENT ON COLUMN user_profiles.settings_timezone IS 'User timezone for date/time display';
COMMENT ON COLUMN user_profiles.is_active IS 'Whether user account is active';
