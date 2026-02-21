-- V58: Add timing duration fields for structural session generation (Plan: Schedule Preview)
-- These fields define durations (in minutes) for moderation and break sessions
-- generated via POST /api/v1/events/{eventCode}/sessions/structural

ALTER TABLE event_types
    ADD COLUMN moderation_start_duration INT NOT NULL DEFAULT 5,
    ADD COLUMN moderation_end_duration   INT NOT NULL DEFAULT 5,
    ADD COLUMN break_duration            INT NOT NULL DEFAULT 20,
    ADD COLUMN lunch_duration            INT NOT NULL DEFAULT 60;
