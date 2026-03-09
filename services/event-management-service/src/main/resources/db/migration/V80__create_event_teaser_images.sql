-- Story 10.22: Event Teaser Images for Moderator Presentation Page
CREATE TABLE event_teaser_images (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    event_code    VARCHAR(50) NOT NULL REFERENCES events(event_code) ON DELETE CASCADE,
    s3_key        TEXT        NOT NULL,
    image_url     TEXT        NOT NULL,
    display_order INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_teaser_images_event_code
    ON event_teaser_images (event_code, display_order);
