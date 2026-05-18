-- Story 11.E.2 review patch (D2): The classpath speaker-invitation-{de,en}.html files
-- were rewritten for the Cognito-flow invitation (login URL + temp password / use-existing
-- branch). EmailTemplateSeedService only inserts new rows; existing rows from prior seed
-- runs in staging/prod still hold the magic-link wording and would silently render the
-- WRONG template at runtime (DB-first lookup in SpeakerInvitationEmailService.loadHtmlContent).
--
-- This migration brings the (speaker-invitation, de) and (speaker-invitation, en) rows
-- in line with the new classpath bodies. UPDATEs are idempotent — re-running has no effect.
-- Fresh-database installs are unaffected (no row to update; seed inserts the new body).

UPDATE email_templates
SET subject = 'Speaker Invitation - {{eventTitle}}',
    html_body = $$<h2>Speaker Invitation</h2>

<p>Dear {{speakerName}},</p>

<p>We are pleased to invite you as a speaker at our upcoming Berner Architekten Treffen event:</p>

<div class="event-details">
    <h2>{{eventTitle}}</h2>
    <div class="detail-row">
        <span class="detail-label">Date:</span>
        <span class="detail-value">{{eventDate}}</span>
    </div>
    <div class="detail-row">
        <span class="detail-label">Time:</span>
        <span class="detail-value">{{eventTime}}</span>
    </div>
    <div class="detail-row">
        <span class="detail-label">Venue:</span>
        <span class="detail-value">{{venueName}}<br>{{venueAddress}}</span>
    </div>
</div>

{{#sessionTitle}}
<div class="session-info">
    <h3>Proposed Topic</h3>
    <p><strong>{{sessionTitle}}</strong></p>
    {{#sessionDescription}}
    <p>{{sessionDescription}}</p>
    {{/sessionDescription}}
</div>
{{/sessionTitle}}

<p>We would be honored to have you as a speaker at our event. Your expertise and knowledge would be a valuable addition for our audience.</p>

<div class="login-section">
    <h3>Your speaker portal account</h3>
    <p>To respond to this invitation and manage your session materials, please log in to the speaker portal:</p>
    <p style="text-align: center; margin: 20px 0;">
        <a href="{{loginUrl}}" class="cta-button cta-accept" style="display: inline-block; padding: 12px 24px; background-color: #0066cc; color: white; text-decoration: none; border-radius: 4px;">Log in to Speaker Portal</a>
    </p>
    <p><strong>Login URL:</strong> <a href="{{loginUrl}}" style="color: #0066cc; word-break: break-all;">{{loginUrl}}</a></p>
    <p><strong>Username:</strong> {{usernameForLogin}}</p>
    {{#temporaryPassword}}
    <p><strong>Temporary password:</strong> <code style="font-family: monospace; background-color: #f5f5f5; padding: 4px 8px; border-radius: 3px;">{{temporaryPassword}}</code></p>
    <p style="font-size: 13px; color: #666;">You will be asked to set your own password on first login. The temporary password is valid for {{tempPasswordValidityDays}} days.</p>
    {{/temporaryPassword}}
    {{#useExistingPassword}}
    <p style="font-size: 14px;">You already have a BATbern account. Please log in with your existing password, or use the "Forgot password" link on the login page if you need to reset it.</p>
    {{/useExistingPassword}}
</div>

{{#responseDeadline}}
<div class="deadline-box">
    <h3>Please respond by</h3>
    <p style="font-size: 18px; font-weight: bold; margin: 0;">{{responseDeadline}}</p>
    {{#contentDeadline}}
    <p style="margin-top: 10px; font-size: 14px;">
        If you accept, we kindly ask you to submit your presentation materials by <strong>{{contentDeadline}}</strong>.
    </p>
    {{/contentDeadline}}
</div>
{{/responseDeadline}}

<div class="contact-box">
    <h3>Questions?</h3>
    <p>If you have any questions, please don't hesitate to contact us:</p>
    <p>
        <strong>{{organizerName}}</strong><br>
        <a href="mailto:{{organizerEmail}}">{{organizerEmail}}</a>
    </p>
</div>

<p>We look forward to hearing from you!</p>

<p>Best regards,<br>
<strong>The BATbern Team</strong></p>
$$,
    updated_at = NOW()
WHERE template_key = 'speaker-invitation'
  AND locale = 'en';

UPDATE email_templates
SET subject = 'Einladung als Referent - {{eventTitle}}',
    html_body = $$<h2>Einladung als Referent</h2>

<p>Guten Tag {{speakerName}},</p>

<p>Wir freuen uns, Sie als Referent zu unserem kommenden Berner Architekten Treffen einzuladen:</p>

<div class="event-details">
    <h2>{{eventTitle}}</h2>
    <div class="detail-row">
        <span class="detail-label">Datum:</span>
        <span class="detail-value">{{eventDate}}</span>
    </div>
    <div class="detail-row">
        <span class="detail-label">Uhrzeit:</span>
        <span class="detail-value">{{eventTime}}</span>
    </div>
    <div class="detail-row">
        <span class="detail-label">Veranstaltungsort:</span>
        <span class="detail-value">{{venueName}}<br>{{venueAddress}}</span>
    </div>
</div>

{{#sessionTitle}}
<div class="session-info">
    <h3>Vorgesehenes Thema</h3>
    <p><strong>{{sessionTitle}}</strong></p>
    {{#sessionDescription}}
    <p>{{sessionDescription}}</p>
    {{/sessionDescription}}
</div>
{{/sessionTitle}}

<p>Wir wuerden uns sehr freuen, Sie als Referenten bei unserem Event begruessen zu duerfen. Ihre Expertise und Ihr Wissen waeren eine wertvolle Bereicherung fuer unser Publikum.</p>

<div class="login-section">
    <h3>Ihr Referenten-Portal-Konto</h3>
    <p>Um diese Einladung zu beantworten und Ihre Session-Unterlagen zu verwalten, melden Sie sich bitte im Referenten-Portal an:</p>
    <p style="text-align: center; margin: 20px 0;">
        <a href="{{loginUrl}}" class="cta-button cta-accept" style="display: inline-block; padding: 12px 24px; background-color: #0066cc; color: white; text-decoration: none; border-radius: 4px;">Zum Referenten-Portal anmelden</a>
    </p>
    <p><strong>Anmelde-URL:</strong> <a href="{{loginUrl}}" style="color: #0066cc; word-break: break-all;">{{loginUrl}}</a></p>
    <p><strong>Benutzername:</strong> {{usernameForLogin}}</p>
    {{#temporaryPassword}}
    <p><strong>Temporäres Passwort:</strong> <code style="font-family: monospace; background-color: #f5f5f5; padding: 4px 8px; border-radius: 3px;">{{temporaryPassword}}</code></p>
    <p style="font-size: 13px; color: #666;">Bei der ersten Anmeldung werden Sie aufgefordert, ein eigenes Passwort festzulegen. Das temporaere Passwort ist {{tempPasswordValidityDays}} Tage gueltig.</p>
    {{/temporaryPassword}}
    {{#useExistingPassword}}
    <p style="font-size: 14px;">Sie verfuegen bereits ueber ein BATbern-Konto. Bitte melden Sie sich mit Ihrem bestehenden Passwort an oder verwenden Sie die Funktion "Passwort vergessen" auf der Anmeldeseite, falls Sie es zuruecksetzen moechten.</p>
    {{/useExistingPassword}}
</div>

{{#responseDeadline}}
<div class="deadline-box">
    <h3>Bitte antworten Sie bis</h3>
    <p style="font-size: 18px; font-weight: bold; margin: 0;">{{responseDeadline}}</p>
    {{#contentDeadline}}
    <p style="margin-top: 10px; font-size: 14px;">
        Falls Sie zusagen, bitten wir Sie, Ihre Prasentationsunterlagen bis zum <strong>{{contentDeadline}}</strong> einzureichen.
    </p>
    {{/contentDeadline}}
</div>
{{/responseDeadline}}

<div class="contact-box">
    <h3>Fragen?</h3>
    <p>Bei Fragen stehen wir Ihnen gerne zur Verfuegung:</p>
    <p>
        <strong>{{organizerName}}</strong><br>
        <a href="mailto:{{organizerEmail}}">{{organizerEmail}}</a>
    </p>
</div>

<p>Wir freuen uns auf Ihre Rueckmeldung!</p>

<p>Mit freundlichen Gruessen,<br>
<strong>Das BATbern Team</strong></p>
$$,
    updated_at = NOW()
WHERE template_key = 'speaker-invitation'
  AND locale = 'de';
