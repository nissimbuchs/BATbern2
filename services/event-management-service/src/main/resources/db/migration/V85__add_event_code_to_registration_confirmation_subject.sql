-- V85: Add event code to registration-confirmation email subject.
-- Required for inbound email reply routing (Story 10.17): the router extracts BATbernXX
-- from the subject line using regex BATbern\d+. Without the event code in the subject,
-- CANCEL and ACCEPT reply commands cannot identify which event to act on.
UPDATE email_templates
SET subject = 'Registrierungsbestätigung - {{eventCode}} - {{eventTitle}}'
WHERE template_key = 'registration-confirmation' AND locale = 'de';

UPDATE email_templates
SET subject = 'Registration Confirmation - {{eventCode}} - {{eventTitle}}'
WHERE template_key = 'registration-confirmation' AND locale = 'en';
