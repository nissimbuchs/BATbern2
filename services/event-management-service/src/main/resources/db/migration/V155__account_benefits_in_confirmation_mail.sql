-- V155__account_benefits_in_confirmation_mail.sql
-- Story "Visitors see what a BATbern account gives them before they create one" (owner-approved
-- wording 2026-11-13): the account block of the "please confirm your registration" mail named vague
-- or untrue benefits ("exclusive content"). It now lists the four concrete ones and links the public
-- page {{baseUrl}}/why-an-account.
--
-- EmailTemplateSeedService only inserts missing rows, so the rows already in the database are
-- updated here. Pattern of V76/V124: a targeted replace of the old block, and only in rows that
-- still hold it byte for byte. A row whose account block an organizer edited stays as it is.
-- Nothing else in the mail changes (confirmation link, 48-hour rule, calendar text).
-- The old block is the one #809 resynced from the live production rows into the classpath files.
-- Idempotent: after the first run the old block is gone and the guard matches nothing.

-- ── DE ────────────────────────────────────────────────────────
UPDATE email_templates
SET html_body = replace(html_body,
$old$<div class="info-box">
  <h3>Kostenloses Konto erstellen</h3>
  <ul>
    <li>Registrierungen verwalten</li>
    <li>Event-Updates erhalten</li>
    <li>Exklusive Inhalte entdecken</li>
  </ul>
  <a href="{{createAccountUrl}}" class="btn btn-primary">Konto erstellen</a>
</div>$old$,
$new$<div class="info-box">
  <h3>Was Ihnen ein kostenloses Konto bietet</h3>
  <ul>
    <li>Fragen und Kommentare zu den Vorträgen, von zwei Wochen vor bis zwei Wochen nach dem Event</li>
    <li>Themen für kommende BATbern-Events vorschlagen</li>
    <li>Alle Ihre BATbern-Besuche an einem Ort, auch Anmeldungen aus der Zeit vor Ihrem Konto</li>
    <li>Das Logo Ihrer Firma neben Ihrem Namen</li>
  </ul>
  <a href="{{createAccountUrl}}" class="btn btn-primary">Kostenloses Konto erstellen</a>
  <p><a href="{{baseUrl}}/why-an-account">Mehr zum Konto</a></p>
</div>$new$),
    updated_at = now()
WHERE template_key = 'registration-confirmation'
  AND locale = 'de'
  AND strpos(html_body, $old$<div class="info-box">
  <h3>Kostenloses Konto erstellen</h3>
  <ul>
    <li>Registrierungen verwalten</li>
    <li>Event-Updates erhalten</li>
    <li>Exklusive Inhalte entdecken</li>
  </ul>
  <a href="{{createAccountUrl}}" class="btn btn-primary">Konto erstellen</a>
</div>$old$) > 0;

-- ── EN ────────────────────────────────────────────────────────
UPDATE email_templates
SET html_body = replace(html_body,
$old$<div class="info-box">
  <h3>Create a Free Account</h3>
  <ul>
    <li>Manage registrations</li>
    <li>Receive event updates</li>
    <li>Access exclusive content</li>
  </ul>
  <a href="{{createAccountUrl}}" class="btn btn-primary">Create Free Account</a>
</div>$old$,
$new$<div class="info-box">
  <h3>What a free account gives you</h3>
  <ul>
    <li>Ask questions and comment on the talks, from two weeks before to two weeks after the event</li>
    <li>Suggest topics for coming BATbern events</li>
    <li>All your BATbern visits in one place, including registrations from before you had an account</li>
    <li>Your company's logo next to your name</li>
  </ul>
  <a href="{{createAccountUrl}}" class="btn btn-primary">Create a free account</a>
  <p><a href="{{baseUrl}}/why-an-account">More about the account</a></p>
</div>$new$),
    updated_at = now()
WHERE template_key = 'registration-confirmation'
  AND locale = 'en'
  AND strpos(html_body, $old$<div class="info-box">
  <h3>Create a Free Account</h3>
  <ul>
    <li>Manage registrations</li>
    <li>Receive event updates</li>
    <li>Access exclusive content</li>
  </ul>
  <a href="{{createAccountUrl}}" class="btn btn-primary">Create Free Account</a>
</div>$old$) > 0;
