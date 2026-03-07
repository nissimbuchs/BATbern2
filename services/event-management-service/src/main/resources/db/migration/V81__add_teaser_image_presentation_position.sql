-- V81: Add presentation_position to event_teaser_images
-- Controls which slide the image appears after in the moderator presentation.
-- Default: AFTER_TOPIC_REVEAL (preserves existing behaviour).
ALTER TABLE event_teaser_images
    ADD COLUMN presentation_position VARCHAR(30)
        NOT NULL DEFAULT 'AFTER_TOPIC_REVEAL'
        CHECK (presentation_position IN (
            'AFTER_WELCOME',
            'AFTER_COMMITTEE',
            'AFTER_TOPIC_REVEAL',
            'AFTER_UPCOMING_EVENTS'
        ));
