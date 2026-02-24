-- Story 10.4: Blob Topic Selector
-- Adds topic_selection_note TEXT column to events table for recording the rationale
-- when an organizer selects a topic via the blob selector (AC: 34).
ALTER TABLE events ADD COLUMN topic_selection_note TEXT;
