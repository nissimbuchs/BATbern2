-- #1158: newsletter_sends referenced events without ON DELETE, so deleting an event that ever had a
-- newsletter or registrant notice failed with a 500. The sends are history of the deleted event and
-- go with it, like every other table that references events. newsletter_recipients already cascade
-- from newsletter_sends (V67); mail_message.newsletter_send_id is set to NULL (V135).
ALTER TABLE newsletter_sends DROP CONSTRAINT newsletter_sends_event_id_fkey;

ALTER TABLE newsletter_sends
    ADD CONSTRAINT newsletter_sends_event_id_fkey
        FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE;
