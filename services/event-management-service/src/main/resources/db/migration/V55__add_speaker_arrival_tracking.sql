-- V55__add_speaker_arrival_tracking.sql
-- Epic 2, Story W2.4: Speaker arrival tracking persistence
-- Unique constraint ensures idempotent arrival confirmations (no duplicates)

CREATE TABLE speaker_arrivals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_code VARCHAR(50) NOT NULL,
    speaker_username VARCHAR(100) NOT NULL,
    confirmed_by_username VARCHAR(100) NOT NULL,
    arrived_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (event_code, speaker_username)  -- Idempotent: one arrival per speaker per event
);

CREATE INDEX idx_speaker_arrivals_event ON speaker_arrivals(event_code);
