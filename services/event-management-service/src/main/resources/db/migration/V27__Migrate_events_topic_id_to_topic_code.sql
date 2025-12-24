-- Migration: Replace topic_id (UUID) with topic_code (meaningful identifier)
-- ADR-003: Use meaningful identifiers in external APIs instead of UUIDs
-- Story 5.2: Topic Selection & Speaker Brainstorming
-- Also removes unused theme column (was frontend-only, never persisted)

-- Step 1: Add topic_code column
ALTER TABLE events
ADD COLUMN topic_code VARCHAR(100);

-- Step 2: Migrate existing data from topic_id to topic_code
-- Join with topics table to get the topicCode for each topicId
UPDATE events e
SET topic_code = t.topic_code
FROM topics t
WHERE e.topic_id = t.id
  AND e.topic_id IS NOT NULL;

-- Step 3: Drop the old topic_id column
ALTER TABLE events
DROP COLUMN topic_id;

-- Step 4: Drop unused theme column if it exists (cleanup)
-- Theme was a frontend-only field that was never properly persisted
ALTER TABLE events
DROP COLUMN IF EXISTS theme;

-- Note: No index needed on topic_code as it's not a foreign key
-- (topics are looked up by topicCode in the TopicService)
