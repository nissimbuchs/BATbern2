-- "What's new" card on the attendee dashboard: the time the user last closed it. Entries released
-- after this time are shown; null means the user has never closed the card (first-time view).
-- Stored on the server so every browser of the same account shows the same state.
-- V31 is taken by #1307 (drop the unused logos table).
ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS settings_whats_new_seen_at TIMESTAMP WITH TIME ZONE NULL;
