-- V124__registration_pending_mail_without_calendar.sql
-- The "please confirm your registration" mail no longer carries the calendar invite: its inline
-- .ics rendered an "Accept" button that attendees took for the confirmation, so ~30 of them never
-- clicked the confirmation link and their registrations were cleaned up. The invite now goes out
-- with the new registration-confirmed mail (seeded from the classpath by EmailTemplateSeedService).
--
-- This migration aligns the already-seeded registration-confirmation rows with the classpath
-- templates: new subject (keeps {{eventCode}} for inbound reply routing, Story 10.17), a clear
-- "only valid once confirmed" line, and no ".ics attached" sentence.
-- Targeted REPLACEs, so an organizer-edited body is left alone where the old text is gone.
-- Idempotent: re-running finds nothing left to replace.

-- ── DE ────────────────────────────────────────────────────────
UPDATE email_templates
SET subject = 'Bitte bestätigen: Ihre Anmeldung - {{eventCode}} - {{eventTitle}}',
    html_body = REPLACE(REPLACE(html_body,
        '<p><strong>Wichtig:</strong> Bitte bestätigen Sie Ihre Registrierung innerhalb von 48 Stunden:</p>',
        '<p><strong>Wichtig:</strong> Ihre Anmeldung ist erst gültig, wenn Sie sie bestätigen. Bitte bestätigen Sie Ihre Registrierung innerhalb von 48 Stunden:</p>'),
        '<p>Ein Kalendereintrag (.ics Datei) ist dieser E-Mail beigefügt.</p>',
        '<p>Den Kalendereintrag erhalten Sie, sobald Sie Ihre Anmeldung bestätigt haben.</p>'),
    updated_at = now()
WHERE template_key = 'registration-confirmation' AND locale = 'de' AND is_system_template = true;

-- ── EN ────────────────────────────────────────────────────────
UPDATE email_templates
SET subject = 'Please confirm your registration - {{eventCode}} - {{eventTitle}}',
    html_body = REPLACE(REPLACE(html_body,
        '<p><strong>Important:</strong> Please confirm your registration within 48 hours:</p>',
        '<p><strong>Important:</strong> Your registration only becomes valid once you confirm it. Please confirm your registration within 48 hours:</p>'),
        '<p>A calendar entry (.ics file) is attached to this email.</p>',
        '<p>You will receive the calendar entry as soon as you have confirmed your registration.</p>'),
    updated_at = now()
WHERE template_key = 'registration-confirmation' AND locale = 'en' AND is_system_template = true;
