-- V15__backfill_topic_usage_history.sql
-- GitHub Issue #379: Backfill topic_usage_history from events.topic_id relationship
-- Creates historical usage records for heatmap visualization

-- Insert usage history records for all events that have a topic assigned
INSERT INTO topic_usage_history (
    id,
    topic_id,
    event_id,
    used_date,
    attendee_count,
    feedback_score,
    engagement_score,
    created_at,
    updated_at
)
SELECT
    uuid_generate_v4() as id,
    e.topic_id,
    e.id as event_id,
    e.event_date as used_date,
    COALESCE(e.current_attendee_count, 0) as attendee_count,
    NULL as feedback_score, -- No historical feedback data available
    CASE
        -- Estimate engagement based on workflow state
        WHEN e.workflow_state IN ('archived', 'event_ready', 'agenda_finalized') THEN 0.80
        WHEN e.workflow_state IN ('agenda_published', 'newsletter_sent') THEN 0.75
        ELSE 0.70
    END as engagement_score,
    NOW() as created_at,
    NOW() as updated_at
FROM events e
WHERE e.topic_id IS NOT NULL
  AND NOT EXISTS (
      -- Don't duplicate if record already exists
      SELECT 1 FROM topic_usage_history tuh
      WHERE tuh.topic_id = e.topic_id AND tuh.event_id = e.id
  )
ORDER BY e.event_date;

-- Verify backfill results
DO $$
DECLARE
    inserted_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO inserted_count FROM topic_usage_history;
    RAISE NOTICE 'Backfilled % topic usage history records from events', inserted_count;
END $$;
