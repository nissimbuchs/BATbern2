-- V32__Update_evening_event_start_time.sql
-- Update evening event type start time from 18:00 to 16:00
-- Story 5.7: Slot Assignment & Progressive Publishing

UPDATE event_types
SET typical_start_time = '16:00',
    updated_at = NOW()
WHERE type = 'evening';
