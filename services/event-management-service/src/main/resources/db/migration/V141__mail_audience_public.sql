-- Story 16.2b AC8: audience PUBLIC for people who are not addressed as a group (contact-alias
-- writers, replies that match no recorded mail). No outbound mail has it.
ALTER TABLE mail_message DROP CONSTRAINT chk_mail_message_audience;
ALTER TABLE mail_message ADD CONSTRAINT chk_mail_message_audience CHECK (audience IN
    ('NEWSLETTER', 'REGISTRANTS', 'SPEAKERS', 'VENUE', 'PARTNERS', 'ORGANIZERS', 'SYSTEM', 'PUBLIC'));

ALTER TABLE mail_conversation DROP CONSTRAINT chk_mail_conversation_audience;
ALTER TABLE mail_conversation ADD CONSTRAINT chk_mail_conversation_audience CHECK (audience IN
    ('NEWSLETTER', 'REGISTRANTS', 'SPEAKERS', 'VENUE', 'PARTNERS', 'ORGANIZERS', 'SYSTEM', 'PUBLIC'));
