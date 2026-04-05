-- Story 10.29: Add test_mode flag to newsletter_sends for organizer-only test sends.
ALTER TABLE newsletter_sends ADD COLUMN test_mode BOOLEAN NOT NULL DEFAULT FALSE;
