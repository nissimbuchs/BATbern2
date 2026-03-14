-- V90: Allow global teaser images (event_code NULL = shown on all events)
-- Story: Global Teaser Images — Admin-level images for all presentations

ALTER TABLE event_teaser_images ALTER COLUMN event_code DROP NOT NULL;
