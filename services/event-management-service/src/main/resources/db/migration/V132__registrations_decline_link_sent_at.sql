-- Story 16.0 (#1161, #1162): a reply "cancel" or a calendar "Declined" no longer cancels a
-- registration at once; it sends the deregistration-link mail. This column records when that mail
-- went out, so a second reply or decline within 7 days sends no second mail. Same shape as
-- confirmation_resent_at. Null = never sent.
ALTER TABLE registrations ADD COLUMN decline_link_sent_at TIMESTAMPTZ;
