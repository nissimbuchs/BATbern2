-- Notifications table (follows Task System pattern - Story 5.5)
-- ADR-003 compliant: Uses meaningful identifiers (recipient_username, event_code)
-- Hybrid storage: Email/SMS create audit trail rows, in-app notifications queried dynamically

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- ADR-003: Meaningful IDs (NOT foreign keys)
    recipient_username VARCHAR(100) NOT NULL,
    event_code VARCHAR(50),  -- Nullable for non-event notifications

    -- Notification details
    notification_type VARCHAR(50) NOT NULL,  -- SPEAKER_INVITED, DEADLINE_WARNING, EVENT_PUBLISHED, etc.
    channel VARCHAR(20) NOT NULL,            -- EMAIL, SMS
    priority VARCHAR(20) DEFAULT 'NORMAL',   -- LOW, NORMAL, HIGH, URGENT
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,

    -- Delivery tracking
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, SENT, FAILED, READ
    sent_at TIMESTAMP,
    read_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,

    -- Metadata (flexible JSONB storage for task_id, speaker_id, etc.)
    metadata JSONB,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_notifications_recipient ON notifications(recipient_username);
CREATE INDEX idx_notifications_event ON notifications(event_code) WHERE event_code IS NOT NULL;
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);
CREATE INDEX idx_notifications_recipient_created ON notifications(recipient_username, created_at DESC);
CREATE INDEX idx_notifications_recipient_status ON notifications(recipient_username, status);

-- Comment for documentation
COMMENT ON TABLE notifications IS 'Notification delivery tracking (email/SMS audit trail). In-app notifications queried dynamically. Follows Task System pattern (Story 5.5). ADR-003 compliant (meaningful IDs).';
COMMENT ON COLUMN notifications.recipient_username IS 'ADR-003: Meaningful identifier for user (NOT foreign key to user_profiles)';
COMMENT ON COLUMN notifications.event_code IS 'ADR-003: Meaningful identifier for event (NOT foreign key to events). Nullable for non-event notifications.';
COMMENT ON COLUMN notifications.metadata IS 'Flexible JSONB storage for additional context (task_id, speaker_id, etc.)';
COMMENT ON COLUMN notifications.status IS 'Delivery status: PENDING (queued), SENT (delivered), FAILED (delivery error), READ (user acknowledged)';
