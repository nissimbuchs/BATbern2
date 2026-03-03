-- Story 10.21: Event Photos Gallery
-- Creates event_photos table for storing event photo references (S3 keys + CloudFront URLs)
CREATE TABLE event_photos (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_code   VARCHAR(50) NOT NULL REFERENCES events(event_code) ON DELETE CASCADE,
    s3_key       TEXT        NOT NULL,
    display_url  TEXT        NOT NULL,
    filename     TEXT,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by  VARCHAR(255),
    sort_order   INT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_event_photos_event_code ON event_photos(event_code);
CREATE INDEX idx_event_photos_uploaded_at ON event_photos(uploaded_at DESC);
