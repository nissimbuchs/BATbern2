-- V54__Add_speaker_reminder_log.sql
-- Story 6.5: Automated Deadline Reminders (AC3, AC5, AC6, AC8)
-- Creates reminder log table for deduplication and audit trail
-- Adds reminders_disabled flag to speaker_pool
-- Extends outreach history contact_method constraint for automated reminders

-- Reminder log for deduplication and audit trail
CREATE TABLE speaker_reminder_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_pool_id UUID NOT NULL REFERENCES speaker_pool(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    reminder_type VARCHAR(20) NOT NULL,       -- 'RESPONSE' or 'CONTENT'
    tier VARCHAR(10) NOT NULL,                -- 'TIER_1', 'TIER_2', 'TIER_3'
    email_address VARCHAR(255) NOT NULL,
    deadline_date DATE NOT NULL,
    triggered_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',  -- 'SYSTEM' for automated, organizer username for manual
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reminder_type CHECK (reminder_type IN ('RESPONSE', 'CONTENT')),
    CONSTRAINT chk_reminder_tier CHECK (tier IN ('TIER_1', 'TIER_2', 'TIER_3'))
);

-- Deduplication index: prevent sending same automated tier for same speaker+deadline
-- Only applies to SYSTEM-triggered reminders; manual triggers bypass dedup
CREATE UNIQUE INDEX idx_reminder_log_dedup
    ON speaker_reminder_log(speaker_pool_id, reminder_type, tier, deadline_date)
    WHERE triggered_by = 'SYSTEM';

-- Query index: find reminders by speaker
CREATE INDEX idx_reminder_log_speaker
    ON speaker_reminder_log(speaker_pool_id);

-- Query index: find reminders by event
CREATE INDEX idx_reminder_log_event
    ON speaker_reminder_log(event_id);

-- Add reminders_disabled flag to speaker_pool (AC6)
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS reminders_disabled BOOLEAN DEFAULT FALSE;

-- Extend outreach history contact_method to support automated and manual reminders (AC5, AC8)
-- Drop old constraint and add new one with additional allowed values
ALTER TABLE speaker_outreach_history DROP CONSTRAINT IF EXISTS speaker_outreach_history_contact_method_check;
ALTER TABLE speaker_outreach_history ADD CONSTRAINT speaker_outreach_history_contact_method_check
    CHECK (contact_method IN ('email', 'phone', 'in_person', 'automated_email', 'manual_email'));

-- Comments
COMMENT ON TABLE speaker_reminder_log IS 'Tracks all speaker deadline reminders sent (automated and manual) for deduplication and audit - Story 6.5';
COMMENT ON COLUMN speaker_reminder_log.reminder_type IS 'Type of deadline: RESPONSE (invitation response) or CONTENT (material submission)';
COMMENT ON COLUMN speaker_reminder_log.tier IS 'Escalation tier: TIER_1 (friendly, 14d), TIER_2 (urgent, 7d), TIER_3 (final, 3d)';
COMMENT ON COLUMN speaker_reminder_log.triggered_by IS 'SYSTEM for scheduled reminders, organizer username for manual triggers';
COMMENT ON COLUMN speaker_pool.reminders_disabled IS 'When true, automated reminders are skipped for this speaker - Story 6.5 AC6';
