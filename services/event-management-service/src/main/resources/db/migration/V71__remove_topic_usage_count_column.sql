-- Remove denormalized usage_count from topics table.
-- Usage count is now computed live as COUNT(*) from topic_usage_history.
-- With only 17 topics and ~58 events this is negligible cost and avoids data drift.

DROP INDEX IF EXISTS idx_topics_usage_count;
ALTER TABLE topics DROP COLUMN IF EXISTS usage_count;
