-- Add theme image columns to events table
ALTER TABLE events
ADD COLUMN theme_image_url VARCHAR(1000),
ADD COLUMN theme_image_upload_id VARCHAR(100);

-- Index for cleanup queries
CREATE INDEX idx_events_theme_image ON events(theme_image_upload_id);

-- Comments
COMMENT ON COLUMN events.theme_image_url IS 'CloudFront URL for event theme image (from GenericLogoService)';
COMMENT ON COLUMN events.theme_image_upload_id IS 'Upload ID from three-phase upload pattern (ADR-002)';
