-- V70__remove_topic_staleness_columns.sql
-- Remove denormalized staleness columns from topics table.
-- With only 17 topics and ~60 usage_history records, caching these values
-- causes consistency bugs (as seen on staging) with zero performance benefit.
-- staleness_score and last_used_date are now computed live from topic_usage_history.

DROP INDEX IF EXISTS idx_topics_last_used;
DROP INDEX IF EXISTS idx_topics_staleness;

ALTER TABLE topics DROP COLUMN IF EXISTS last_used_date;
ALTER TABLE topics DROP COLUMN IF EXISTS staleness_score;
ALTER TABLE topics DROP COLUMN IF EXISTS calculated_wait_period;
