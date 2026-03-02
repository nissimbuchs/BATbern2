-- V76: Add {{deregistrationUrl}} cancellation link to registration-confirmation system templates.
--
-- Story 10.12 (AC7): The registration confirmation email must include a self-service
-- cancellation link. The classpath templates were updated in the 10.12 PR, but the
-- seed service is idempotent (skips already-existing templates), so any environment
-- seeded before that PR still has the old body without {{deregistrationUrl}}.
--
-- Guard: only updates rows whose body does NOT already contain {{deregistrationUrl}}
-- to make this migration safe to run on environments that were re-seeded from the
-- updated classpath (idempotent).

-- German template
UPDATE email_templates
SET html_body = replace(
        html_body,
        '<p>Mit freundlichen Grüssen,<br>'  || chr(10) || '<strong>Das BATbern Team</strong></p>',
        '<p style="font-size: 12px; color: #666; margin-top: 24px; border-top: 1px solid #eee; padding-top: 16px;">' || chr(10) ||
        '    Möchten Sie Ihre Registrierung stornieren? <a href="{{deregistrationUrl}}" style="color: #666;">Hier abmelden</a>' || chr(10) ||
        '</p>' || chr(10) || chr(10) ||
        '<p>Mit freundlichen Grüssen,<br>' || chr(10) || '<strong>Das BATbern Team</strong></p>'
    ),
    updated_at = NOW()
WHERE template_key = 'registration-confirmation'
  AND locale       = 'de'
  AND html_body NOT LIKE '%deregistrationUrl%';

-- English template
UPDATE email_templates
SET html_body = replace(
        html_body,
        '<p>Best regards,<br>' || chr(10) || '<strong>The BATbern Team</strong></p>',
        '<p style="font-size: 12px; color: #666; margin-top: 24px; border-top: 1px solid #eee; padding-top: 16px;">' || chr(10) ||
        '    Need to cancel? <a href="{{deregistrationUrl}}" style="color: #666;">Cancel your registration</a>' || chr(10) ||
        '</p>' || chr(10) || chr(10) ||
        '<p>Best regards,<br>' || chr(10) || '<strong>The BATbern Team</strong></p>'
    ),
    updated_at = NOW()
WHERE template_key = 'registration-confirmation'
  AND locale       = 'en'
  AND html_body NOT LIKE '%deregistrationUrl%';
