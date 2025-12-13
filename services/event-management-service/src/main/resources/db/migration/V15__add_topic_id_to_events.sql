-- Migration: Add topic_id column to events table
-- Story: 5.2 - Topic Selection & Speaker Brainstorming
-- Purpose: Link events to selected topics for event planning workflow (AC14)

-- Add topic_id column (nullable since existing events won't have topics yet)
ALTER TABLE events
ADD COLUMN topic_id UUID;

-- Add foreign key constraint to topics table
ALTER TABLE events
ADD CONSTRAINT fk_events_topic
    FOREIGN KEY (topic_id)
    REFERENCES topics(id)
    ON DELETE SET NULL;

-- Add index for performance on topic lookups
CREATE INDEX idx_events_topic_id ON events(topic_id);

-- Add comment for documentation
COMMENT ON COLUMN events.topic_id IS 'Reference to selected topic for this event (Story 5.2 AC14)';
