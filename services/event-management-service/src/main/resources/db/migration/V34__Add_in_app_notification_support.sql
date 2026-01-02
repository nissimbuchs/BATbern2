-- Add IN_APP notification channel support
-- Story BAT-7: Notifications API Consolidation - Organizer Dashboard Notifications
--
-- Changes:
-- 1. Add composite index for IN_APP channel queries (organizer dashboard)
-- 2. Update table comment to document IN_APP channel usage
--
-- Background:
-- The notifications table already supports VARCHAR(20) for channel, so IN_APP is supported.
-- This migration adds performance optimization for organizer dashboard queries.

-- Add composite index for efficient IN_APP dashboard queries
-- Organizers will query: WHERE channel='IN_APP' AND status='UNREAD'
CREATE INDEX IF NOT EXISTS idx_notifications_channel_status
ON notifications(channel, status);

-- Update table comment to document all three channels
COMMENT ON TABLE notifications IS
'Notification delivery tracking. EMAIL/SMS: audit trail with delivery status. IN_APP: persistent notifications for organizer dashboard. Follows Task System pattern (Story 5.5). ADR-003 compliant (meaningful IDs).';

-- Update channel column comment
COMMENT ON COLUMN notifications.channel IS
'Delivery channel: EMAIL (sent via AWS SES), SMS (future), IN_APP (organizer dashboard notifications)';

-- Add status column comment for clarity
COMMENT ON COLUMN notifications.status IS
'Delivery status: PENDING (queued), SENT (delivered via EMAIL/SMS), FAILED (delivery error), UNREAD (IN_APP not yet read), READ (user acknowledged)';
