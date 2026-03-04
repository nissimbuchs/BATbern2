-- V76: Add {{deregistrationUrl}} / {{confirmationUrl}} links to system email templates.
--
-- Story 10.12 (AC7): The registration confirmation email must include a self-service
-- cancellation link. The classpath templates were updated in the 10.12 PR, but the
-- seed service is idempotent (skips already-existing templates), so any environment
-- seeded before that PR still has the old body without {{deregistrationUrl}}.
--
-- Also fixes waitlist-promotion templates: promoted attendees must receive a
-- confirmation link (to confirm attendance) and a cancellation link.
--
-- Guard: only updates rows whose body does NOT already contain the relevant variable,
-- making each statement idempotent on environments re-seeded from updated classpath.

-- ── registration-confirmation (DE) ──────────────────────────────────────────
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

-- ── registration-confirmation (EN) ──────────────────────────────────────────
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

-- ── waitlist-promotion (DE) ──────────────────────────────────────────────────
-- Replaces the entire body: adds confirmation button + deregistration footer.
UPDATE email_templates
SET html_body =
'<h2>Herzlichen Glückwunsch, {{recipientName}}!</h2>
<p>Ein Platz ist frei geworden und Sie wurden von der Warteliste für folgende Veranstaltung registriert:</p>
<p><strong>{{eventTitle}}</strong><br>
Datum: {{eventDate}}<br>
Ort: {{venueAddress}}</p>
<p>Ihr Registrierungscode: <strong>{{registrationCode}}</strong></p>
<p><strong>Wichtig:</strong> Bitte bestätigen Sie Ihre Registrierung innerhalb von 48 Stunden:</p>
<div style="text-align: center;">
    <a href="{{confirmationUrl}}" class="cta-button">✓ Registrierung jetzt bestätigen</a>
</div>
<p style="font-size: 12px; color: #666; margin-top: 20px;">
    Falls der Button nicht funktioniert, kopieren Sie bitte diesen Link in Ihren Browser:<br>
    <a href="{{confirmationUrl}}" style="color: #0066cc; word-break: break-all;">{{confirmationUrl}}</a>
</p>
<p>Wir freuen uns, Sie am Event begrüssen zu dürfen!</p>
<p>Bei Fragen stehen wir Ihnen gerne zur Verfügung: <a href="{{eventUrl}}">{{eventUrl}}</a></p>
<p style="font-size: 12px; color: #666; margin-top: 24px; border-top: 1px solid #eee; padding-top: 16px;">
    Möchten Sie Ihre Registrierung stornieren? <a href="{{deregistrationUrl}}" style="color: #666;">Hier abmelden</a>
</p>
<p>Mit freundlichen Grüssen,<br>Das BATbern Team</p>',
    updated_at = NOW()
WHERE template_key = 'waitlist-promotion'
  AND locale       = 'de'
  AND html_body NOT LIKE '%confirmationUrl%';

-- ── waitlist-promotion (EN) ──────────────────────────────────────────────────
UPDATE email_templates
SET html_body =
'<h2>Great news, {{recipientName}}!</h2>
<p>A spot has opened up and you''ve been moved from the waitlist to registered for:</p>
<p><strong>{{eventTitle}}</strong><br>
Date: {{eventDate}}<br>
Venue: {{venueAddress}}</p>
<p>Your registration code: <strong>{{registrationCode}}</strong></p>
<p><strong>Important:</strong> Please confirm your registration within 48 hours:</p>
<div style="text-align: center;">
    <a href="{{confirmationUrl}}" class="cta-button">✓ Confirm Registration Now</a>
</div>
<p style="font-size: 12px; color: #666; margin-top: 20px;">
    If the button doesn''t work, copy and paste this link into your browser:<br>
    <a href="{{confirmationUrl}}" style="color: #0066cc; word-break: break-all;">{{confirmationUrl}}</a>
</p>
<p>We look forward to seeing you at the event!</p>
<p>For any questions, visit: <a href="{{eventUrl}}">{{eventUrl}}</a></p>
<p style="font-size: 12px; color: #666; margin-top: 24px; border-top: 1px solid #eee; padding-top: 16px;">
    Need to cancel? <a href="{{deregistrationUrl}}" style="color: #666;">Cancel your registration</a>
</p>
<p>Best regards,<br>The BATbern Team</p>',
    updated_at = NOW()
WHERE template_key = 'waitlist-promotion'
  AND locale       = 'en'
  AND html_body NOT LIKE '%confirmationUrl%';
