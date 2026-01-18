-- V45__Add_speaker_preferences_to_invitations.sql
-- Story 6.2: Speaker Self-Service Response Portal
-- Extends speaker_invitations table with preferences and personalized message fields

-- SPEC-001 FIX: Add personal_message for "Why we chose you" display
ALTER TABLE speaker_invitations
    ADD COLUMN IF NOT EXISTS personal_message TEXT;

-- Speaker response preferences (Story 6.2)
ALTER TABLE speaker_invitations
    ADD COLUMN IF NOT EXISTS preferred_time_slot VARCHAR(50)
        CHECK (preferred_time_slot IS NULL OR preferred_time_slot IN ('morning', 'afternoon', 'no_preference'));

ALTER TABLE speaker_invitations
    ADD COLUMN IF NOT EXISTS travel_requirements VARCHAR(50)
        CHECK (travel_requirements IS NULL OR travel_requirements IN ('local', 'accommodation', 'virtual'));

ALTER TABLE speaker_invitations
    ADD COLUMN IF NOT EXISTS technical_requirements TEXT;

ALTER TABLE speaker_invitations
    ADD COLUMN IF NOT EXISTS initial_presentation_title VARCHAR(200);

ALTER TABLE speaker_invitations
    ADD COLUMN IF NOT EXISTS comments_for_organizer TEXT;

-- Add notes field for general speaker notes (aligns with RespondToInvitationRequest.notes)
ALTER TABLE speaker_invitations
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Column comments for documentation
COMMENT ON COLUMN speaker_invitations.personal_message IS 'Organizer message explaining why speaker was chosen - displayed on response portal as "Why we chose you"';
COMMENT ON COLUMN speaker_invitations.preferred_time_slot IS 'Speaker preference: morning, afternoon, or no_preference';
COMMENT ON COLUMN speaker_invitations.travel_requirements IS 'Speaker preference: local (no accommodation needed), accommodation (needs hotel), or virtual (remote participation)';
COMMENT ON COLUMN speaker_invitations.technical_requirements IS 'Comma-separated list of technical requirements: mac_adapter, remote_option, special_av';
COMMENT ON COLUMN speaker_invitations.initial_presentation_title IS 'Initial presentation title proposed by speaker when accepting';
COMMENT ON COLUMN speaker_invitations.comments_for_organizer IS 'Additional comments from speaker to organizer';
COMMENT ON COLUMN speaker_invitations.notes IS 'General notes from speaker response';
