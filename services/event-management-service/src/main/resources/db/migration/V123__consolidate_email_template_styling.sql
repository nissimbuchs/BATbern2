-- V123__consolidate_email_template_styling.sql
-- Consolidate email-template styling: all presentation lives in the batbern-default
-- layout; content templates become clean, class-based, organizer-editable HTML.
-- Rewrites the 6 templates that still carried inline styling and refreshes the
-- shared layout (adds .title/.cta-btn/code classes, fixes the EN layout lang+footer).
-- Guarded by is_system_template = true so organizer customizations are never touched.
-- Idempotent: re-running re-applies the same canonical bodies.
-- See branch feature/consolidate-email-templates. [no-doc]

-- ── Shared layout ─────────────────────────────────────────────
UPDATE email_templates SET html_body = $tpl$<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>BATbern</title>
  <style>
/* ── Reset & base ─────────────────────────────────────────── */
    body {
      font-family: 'Helvetica Neue', Arial, sans-serif;
      background: #f4f4f4;
      margin: 0; padding: 0;
      color: #444;
      -webkit-text-size-adjust: 100%;
    }

    /* ── Email shell ──────────────────────────────────────────── */
    .email-container {
      width: 100%;
      background: #fff;
    }

    /* ── Layout header (logo + org name) ─────────────────────── */
    .header {
      background: #1976d2;
      text-align: center;
      padding: 30px 20px 20px;
    }
    .header img { height: 50px; }
    .header h1 {
      color: #fff;
      margin: 8px 0 0;
      font-size: 15px;
      font-weight: 600;
    }

    /* ── Content area ─────────────────────────────────────────── */
    .content { padding: 32px 36px 8px; }

    /* ── Layout footer ────────────────────────────────────────── */
    .footer {
      padding: 20px 36px 24px;
      border-top: 1px solid #eee;
      font-size: 11px;
      color: #999;
      text-align: center;
    }


    /* ══════════════════════════════════════════════════════════
       TYPOGRAPHY  (3 sizes — 11 / 15 / 24 px)
       ══════════════════════════════════════════════════════════ */

    /* Labels / overlines — 11px uppercase bold */
    .lbl {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 5px;
    }
    .lbl-blue { color: #1976d2; letter-spacing: 2px; }

    /* Email title — 24px, one per email */
    h2 {
      font-size: 24px;
      font-weight: 700;
      line-height: 1.3;
      color: #111;
      margin: 0 0 6px;
    }

    /* Hero title — alias of h2 (one per email) */
    .title {
      font-size: 24px;
      font-weight: 700;
      line-height: 1.3;
      color: #111;
      margin: 0 0 6px;
    }

    /* Sub / event number — 11px muted */
    .sub {
      font-size: 11px;
      color: #aaa;
      letter-spacing: 0.5px;
      margin: 0 0 28px;
    }

    /* Body text — 15px, used for everything else */
    p { font-size: 15px; line-height: 1.75; color: #444; margin: 0 0 20px; }
    td { font-size: 15px; color: #444; }
    li { font-size: 15px; line-height: 1.75; color: #444; margin-bottom: 6px; }
    ul, ol { margin: 0 0 20px; padding-left: 20px; }

    .muted { color: #888; margin: 3px 0 0; }
    strong { color: #222; }

    a { color: #1976d2; text-decoration: none; }
    a:hover { text-decoration: underline; }


    /* ══════════════════════════════════════════════════════════
       HERO BLOCK  (newsletter-event)
       ══════════════════════════════════════════════════════════ */
    .hero {
      border-left: 4px solid #1976d2;
      padding: 4px 0 4px 20px;
      margin-bottom: 28px;
    }


    /* ══════════════════════════════════════════════════════════
       LOGISTICS TABLE  (newsletter-event — date / venue)
       ══════════════════════════════════════════════════════════ */
    table.logistics {
      border-collapse: separate;
      border-spacing: 0;
      background: #f5f7fa;
      border-radius: 7px;
      margin-bottom: 26px;
      width: 100%;
    }
    table.logistics td {
      padding: 16px 20px;
      vertical-align: top;
      width: 50%;
    }
    table.logistics td + td { border-left: 1px solid #e8ebf0; }
    table.logistics strong { font-weight: 600; color: #222; }


    /* ══════════════════════════════════════════════════════════
       PROGRAMME SECTION  (newsletter-event speakers list)
       ══════════════════════════════════════════════════════════ */
    .programme {
      border-top: 1px solid #e8e8e8;
      padding-top: 14px;
      margin-bottom: 24px;
    }


    /* ══════════════════════════════════════════════════════════
       CTA BOX  (newsletter — register CTA)
       ══════════════════════════════════════════════════════════ */
    .cta-box {
      background: #eaf2fb;
      border-radius: 7px;
      padding: 20px 24px;
      margin-bottom: 28px;
    }
    .cta-box p { margin: 0 0 4px; }
    .cta-box .muted { margin-bottom: 14px; }


    /* ══════════════════════════════════════════════════════════
       BUTTONS
       All buttons use <a class="btn btn-*"> inside a <table> cell
       for email client compatibility.
       ══════════════════════════════════════════════════════════ */
    .btn {
      display: inline-block;
      padding: 11px 28px;
      border-radius: 5px;
      font-size: 15px;
      font-weight: 700;
      text-decoration: none !important;
      margin: 4px 8px 4px 0;
    }
    /* Primary — generic single CTA */
    .btn-primary { background: #1976d2; color: #fff !important; }
    /* Accept / confirm — green */
    .btn-accept  { background: #2e7d32; color: #fff !important; }
    /* Decline / cancel — red outline */
    .btn-decline {
      background: #fff;
      color: #c62828 !important;
      border: 1.5px solid #c62828;
    }
    /* Danger — solid red (deregistration) */
    .btn-danger  { background: #c62828; color: #fff !important; }


    /* ══════════════════════════════════════════════════════════
       CTA SECTION  (button group — invitation accept/decline)
       ══════════════════════════════════════════════════════════ */
    .cta-section { margin: 4px 0 24px; }


    /* ══════════════════════════════════════════════════════════
       EVENT CARD  (compact event info — registration / speaker)
       ══════════════════════════════════════════════════════════ */
    .event-card {
      background: #f5f7fa;
      border-radius: 7px;
      padding: 20px 24px;
      margin-bottom: 24px;
    }
    .event-card h3 {
      font-size: 15px;
      font-weight: 700;
      color: #111;
      margin: 0 0 14px;
    }
    /* Detail rows inside event-card */
    table.detail-table { border-collapse: collapse; width: 100%; margin-bottom: 4px; }
    table.detail-table td { padding: 4px 0; vertical-align: top; }
    td.detail-label {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      width: 130px;
      padding-right: 16px;
      padding-top: 5px;
    }
    td.detail-value { font-size: 15px; color: #222; }


    /* ══════════════════════════════════════════════════════════
       SESSION CARD  (speaker session / task notes)
       ══════════════════════════════════════════════════════════ */
    .session-card {
      background: #eaf2fb;
      border-radius: 7px;
      padding: 16px 20px;
      margin-bottom: 20px;
    }
    .session-card h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 2px;
      text-transform: uppercase;
      color: #1976d2;
      margin: 0 0 8px;
    }
    .session-card p { margin: 0; }


    /* ══════════════════════════════════════════════════════════
       SUCCESS BADGE  (speaker acceptance confirmed)
       ══════════════════════════════════════════════════════════ */
    .success-badge {
      display: inline-block;
      background: #dcfce7;
      color: #166534;
      padding: 4px 14px;
      border-radius: 100px;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1px;
      text-transform: uppercase;
      margin-bottom: 20px;
    }


    /* ══════════════════════════════════════════════════════════
       PORTAL SECTION  (speaker portal links)
       ══════════════════════════════════════════════════════════ */
    .portal-section {
      border: 1px solid #e0e0e0;
      border-radius: 7px;
      padding: 20px 24px;
      margin-bottom: 24px;
    }
    .portal-section h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 2px;
      text-transform: uppercase;
      color: #1976d2;
      margin: 0 0 10px;
    }
    .portal-section p { margin: 0 0 14px; }
    /* Portal link buttons */
    .portal-link {
      display: block;
      background: #1976d2;
      color: #fff !important;
      text-decoration: none !important;
      padding: 11px 20px;
      border-radius: 5px;
      margin-bottom: 8px;
      font-weight: 700;
      font-size: 15px;
    }
    .portal-link.secondary {
      background: #f5f7fa;
      color: #333 !important;
      border: 1px solid #e0e0e0;
      font-weight: 400;
    }


    /* ══════════════════════════════════════════════════════════
       DEADLINE BOX  (normal = amber, urgent = red)
       ══════════════════════════════════════════════════════════ */
    .deadline-box {
      background: #fffbeb;
      border-left: 3px solid #d97706;
      border-radius: 0 7px 7px 0;
      padding: 16px 20px;
      margin-bottom: 20px;
    }
    .deadline-box h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #92400e;
      margin: 0 0 8px;
    }
    .deadline-box p { margin: 0 0 4px; }
    .deadline-box .deadline-date {
      font-size: 15px;
      font-weight: 700;
      color: #111;
    }
    /* Urgent variant */
    .deadline-box.urgent {
      background: #fff1f2;
      border-left-color: #dc2626;
    }
    .deadline-box.urgent h3 { color: #dc2626; }


    /* ══════════════════════════════════════════════════════════
       CONTACT BLOCK  (organizer contact info)
       ══════════════════════════════════════════════════════════ */
    .contact-block { margin-bottom: 24px; }
    .contact-block h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 8px;
    }
    .contact-block p { margin: 0; }


    /* ══════════════════════════════════════════════════════════
       NEXT STEPS LIST  (speaker acceptance)
       ══════════════════════════════════════════════════════════ */
    .next-steps { margin-bottom: 24px; }
    .next-steps h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 10px;
    }


    /* ══════════════════════════════════════════════════════════
       INFO BOX  (general-purpose note / account creation)
       ══════════════════════════════════════════════════════════ */
    .info-box {
      background: #f5f7fa;
      border-radius: 7px;
      padding: 16px 20px;
      margin-bottom: 20px;
    }
    .info-box h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 8px;
    }
    .info-box p { margin: 0 0 10px; }
    .info-box p:last-child { margin: 0; }


    /* ══════════════════════════════════════════════════════════
       LINK FALLBACK  (small gray text under buttons)
       ══════════════════════════════════════════════════════════ */
    .link-fallback {
      font-size: 11px;
      color: #aaa;
      margin-top: 8px;
      word-break: break-all;
    }
    .link-fallback a { color: #aaa; }


    /* ══════════════════════════════════════════════════════════
       REGISTRATION CODE  (waitlist / confirmation)
       ══════════════════════════════════════════════════════════ */
    .reg-code {
      background: #f5f7fa;
      border-radius: 7px;
      padding: 16px 20px;
      text-align: center;
      margin-bottom: 20px;
    }
    .reg-code .reg-code-label {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 8px;
    }
    .reg-code .reg-code-value {
      font-size: 24px;
      font-weight: 700;
      color: #1976d2;
      font-family: 'Courier New', monospace;
      letter-spacing: 3px;
      margin: 0;
    }


    /* ══════════════════════════════════════════════════════════
       GDPR FOOTER  (inside content templates)
       ══════════════════════════════════════════════════════════ */
    hr.divider {
      border: none;
      border-top: 1px solid #eee;
      margin: 0 0 16px;
    }
    p.gdpr {
      font-size: 11px;
      color: #bbb;
      text-align: center;
      margin: 0 0 28px;
    }
    p.gdpr a { color: #bbb; }


    /* ══════════════════════════════════════════════════════════
       INLINE CODE  (e.g. temporary password)
       ══════════════════════════════════════════════════════════ */
    code {
      font-family: 'Courier New', monospace;
      background: #f5f7fa;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 14px;
      color: #222;
    }


    /* ══════════════════════════════════════════════════════════
       CTA BUTTON  (newsletter register button — alias of .btn-primary)
       ══════════════════════════════════════════════════════════ */
    .cta-btn {
      display: inline-block;
      background: #1976d2;
      color: #fff !important;
      text-decoration: none !important;
      padding: 11px 28px;
      border-radius: 5px;
      font-size: 15px;
      font-weight: 700;
    }
  </style>
</head>
<body>
<table width="100%" cellpadding="0" cellspacing="0" role="presentation"
       style="background-color: #f4f4f4; width: 100%;">
  <tr>
    <td>
      <div class="email-container">
        <div class="header">
          <img src="{{logoUrl}}" alt="BATbern Logo" width="180" style="width:180px; max-width:180px; height:auto; border:0; display:block; margin:0 auto;" onerror="this.style.display='none'">
          <h1>BATbern</h1>
        </div>
        <div class="content">
          {{content}}
        </div>
        <div class="footer">
          &copy; {{currentYear}} BATbern. Alle Rechte vorbehalten.
        </div>
      </div>
    </td>
  </tr>
</table>
</body>
</html>$tpl$, updated_at = now()
WHERE template_key = 'batbern-default' AND locale = 'de' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>BATbern</title>
  <style>
/* ── Reset & base ─────────────────────────────────────────── */
    body {
      font-family: 'Helvetica Neue', Arial, sans-serif;
      background: #f4f4f4;
      margin: 0; padding: 0;
      color: #444;
      -webkit-text-size-adjust: 100%;
    }

    /* ── Email shell ──────────────────────────────────────────── */
    .email-container {
      width: 100%;
      background: #fff;
    }

    /* ── Layout header (logo + org name) ─────────────────────── */
    .header {
      background: #1976d2;
      text-align: center;
      padding: 30px 20px 20px;
    }
    .header img { height: 50px; }
    .header h1 {
      color: #fff;
      margin: 8px 0 0;
      font-size: 15px;
      font-weight: 600;
    }

    /* ── Content area ─────────────────────────────────────────── */
    .content { padding: 32px 36px 8px; }

    /* ── Layout footer ────────────────────────────────────────── */
    .footer {
      padding: 20px 36px 24px;
      border-top: 1px solid #eee;
      font-size: 11px;
      color: #999;
      text-align: center;
    }


    /* ══════════════════════════════════════════════════════════
       TYPOGRAPHY  (3 sizes — 11 / 15 / 24 px)
       ══════════════════════════════════════════════════════════ */

    /* Labels / overlines — 11px uppercase bold */
    .lbl {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 5px;
    }
    .lbl-blue { color: #1976d2; letter-spacing: 2px; }

    /* Email title — 24px, one per email */
    h2 {
      font-size: 24px;
      font-weight: 700;
      line-height: 1.3;
      color: #111;
      margin: 0 0 6px;
    }

    /* Hero title — alias of h2 (one per email) */
    .title {
      font-size: 24px;
      font-weight: 700;
      line-height: 1.3;
      color: #111;
      margin: 0 0 6px;
    }

    /* Sub / event number — 11px muted */
    .sub {
      font-size: 11px;
      color: #aaa;
      letter-spacing: 0.5px;
      margin: 0 0 28px;
    }

    /* Body text — 15px, used for everything else */
    p { font-size: 15px; line-height: 1.75; color: #444; margin: 0 0 20px; }
    td { font-size: 15px; color: #444; }
    li { font-size: 15px; line-height: 1.75; color: #444; margin-bottom: 6px; }
    ul, ol { margin: 0 0 20px; padding-left: 20px; }

    .muted { color: #888; margin: 3px 0 0; }
    strong { color: #222; }

    a { color: #1976d2; text-decoration: none; }
    a:hover { text-decoration: underline; }


    /* ══════════════════════════════════════════════════════════
       HERO BLOCK  (newsletter-event)
       ══════════════════════════════════════════════════════════ */
    .hero {
      border-left: 4px solid #1976d2;
      padding: 4px 0 4px 20px;
      margin-bottom: 28px;
    }


    /* ══════════════════════════════════════════════════════════
       LOGISTICS TABLE  (newsletter-event — date / venue)
       ══════════════════════════════════════════════════════════ */
    table.logistics {
      border-collapse: separate;
      border-spacing: 0;
      background: #f5f7fa;
      border-radius: 7px;
      margin-bottom: 26px;
      width: 100%;
    }
    table.logistics td {
      padding: 16px 20px;
      vertical-align: top;
      width: 50%;
    }
    table.logistics td + td { border-left: 1px solid #e8ebf0; }
    table.logistics strong { font-weight: 600; color: #222; }


    /* ══════════════════════════════════════════════════════════
       PROGRAMME SECTION  (newsletter-event speakers list)
       ══════════════════════════════════════════════════════════ */
    .programme {
      border-top: 1px solid #e8e8e8;
      padding-top: 14px;
      margin-bottom: 24px;
    }


    /* ══════════════════════════════════════════════════════════
       CTA BOX  (newsletter — register CTA)
       ══════════════════════════════════════════════════════════ */
    .cta-box {
      background: #eaf2fb;
      border-radius: 7px;
      padding: 20px 24px;
      margin-bottom: 28px;
    }
    .cta-box p { margin: 0 0 4px; }
    .cta-box .muted { margin-bottom: 14px; }


    /* ══════════════════════════════════════════════════════════
       BUTTONS
       All buttons use <a class="btn btn-*"> inside a <table> cell
       for email client compatibility.
       ══════════════════════════════════════════════════════════ */
    .btn {
      display: inline-block;
      padding: 11px 28px;
      border-radius: 5px;
      font-size: 15px;
      font-weight: 700;
      text-decoration: none !important;
      margin: 4px 8px 4px 0;
    }
    /* Primary — generic single CTA */
    .btn-primary { background: #1976d2; color: #fff !important; }
    /* Accept / confirm — green */
    .btn-accept  { background: #2e7d32; color: #fff !important; }
    /* Decline / cancel — red outline */
    .btn-decline {
      background: #fff;
      color: #c62828 !important;
      border: 1.5px solid #c62828;
    }
    /* Danger — solid red (deregistration) */
    .btn-danger  { background: #c62828; color: #fff !important; }


    /* ══════════════════════════════════════════════════════════
       CTA SECTION  (button group — invitation accept/decline)
       ══════════════════════════════════════════════════════════ */
    .cta-section { margin: 4px 0 24px; }


    /* ══════════════════════════════════════════════════════════
       EVENT CARD  (compact event info — registration / speaker)
       ══════════════════════════════════════════════════════════ */
    .event-card {
      background: #f5f7fa;
      border-radius: 7px;
      padding: 20px 24px;
      margin-bottom: 24px;
    }
    .event-card h3 {
      font-size: 15px;
      font-weight: 700;
      color: #111;
      margin: 0 0 14px;
    }
    /* Detail rows inside event-card */
    table.detail-table { border-collapse: collapse; width: 100%; margin-bottom: 4px; }
    table.detail-table td { padding: 4px 0; vertical-align: top; }
    td.detail-label {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      width: 130px;
      padding-right: 16px;
      padding-top: 5px;
    }
    td.detail-value { font-size: 15px; color: #222; }


    /* ══════════════════════════════════════════════════════════
       SESSION CARD  (speaker session / task notes)
       ══════════════════════════════════════════════════════════ */
    .session-card {
      background: #eaf2fb;
      border-radius: 7px;
      padding: 16px 20px;
      margin-bottom: 20px;
    }
    .session-card h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 2px;
      text-transform: uppercase;
      color: #1976d2;
      margin: 0 0 8px;
    }
    .session-card p { margin: 0; }


    /* ══════════════════════════════════════════════════════════
       SUCCESS BADGE  (speaker acceptance confirmed)
       ══════════════════════════════════════════════════════════ */
    .success-badge {
      display: inline-block;
      background: #dcfce7;
      color: #166534;
      padding: 4px 14px;
      border-radius: 100px;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1px;
      text-transform: uppercase;
      margin-bottom: 20px;
    }


    /* ══════════════════════════════════════════════════════════
       PORTAL SECTION  (speaker portal links)
       ══════════════════════════════════════════════════════════ */
    .portal-section {
      border: 1px solid #e0e0e0;
      border-radius: 7px;
      padding: 20px 24px;
      margin-bottom: 24px;
    }
    .portal-section h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 2px;
      text-transform: uppercase;
      color: #1976d2;
      margin: 0 0 10px;
    }
    .portal-section p { margin: 0 0 14px; }
    /* Portal link buttons */
    .portal-link {
      display: block;
      background: #1976d2;
      color: #fff !important;
      text-decoration: none !important;
      padding: 11px 20px;
      border-radius: 5px;
      margin-bottom: 8px;
      font-weight: 700;
      font-size: 15px;
    }
    .portal-link.secondary {
      background: #f5f7fa;
      color: #333 !important;
      border: 1px solid #e0e0e0;
      font-weight: 400;
    }


    /* ══════════════════════════════════════════════════════════
       DEADLINE BOX  (normal = amber, urgent = red)
       ══════════════════════════════════════════════════════════ */
    .deadline-box {
      background: #fffbeb;
      border-left: 3px solid #d97706;
      border-radius: 0 7px 7px 0;
      padding: 16px 20px;
      margin-bottom: 20px;
    }
    .deadline-box h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #92400e;
      margin: 0 0 8px;
    }
    .deadline-box p { margin: 0 0 4px; }
    .deadline-box .deadline-date {
      font-size: 15px;
      font-weight: 700;
      color: #111;
    }
    /* Urgent variant */
    .deadline-box.urgent {
      background: #fff1f2;
      border-left-color: #dc2626;
    }
    .deadline-box.urgent h3 { color: #dc2626; }


    /* ══════════════════════════════════════════════════════════
       CONTACT BLOCK  (organizer contact info)
       ══════════════════════════════════════════════════════════ */
    .contact-block { margin-bottom: 24px; }
    .contact-block h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 8px;
    }
    .contact-block p { margin: 0; }


    /* ══════════════════════════════════════════════════════════
       NEXT STEPS LIST  (speaker acceptance)
       ══════════════════════════════════════════════════════════ */
    .next-steps { margin-bottom: 24px; }
    .next-steps h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 10px;
    }


    /* ══════════════════════════════════════════════════════════
       INFO BOX  (general-purpose note / account creation)
       ══════════════════════════════════════════════════════════ */
    .info-box {
      background: #f5f7fa;
      border-radius: 7px;
      padding: 16px 20px;
      margin-bottom: 20px;
    }
    .info-box h3 {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 8px;
    }
    .info-box p { margin: 0 0 10px; }
    .info-box p:last-child { margin: 0; }


    /* ══════════════════════════════════════════════════════════
       LINK FALLBACK  (small gray text under buttons)
       ══════════════════════════════════════════════════════════ */
    .link-fallback {
      font-size: 11px;
      color: #aaa;
      margin-top: 8px;
      word-break: break-all;
    }
    .link-fallback a { color: #aaa; }


    /* ══════════════════════════════════════════════════════════
       REGISTRATION CODE  (waitlist / confirmation)
       ══════════════════════════════════════════════════════════ */
    .reg-code {
      background: #f5f7fa;
      border-radius: 7px;
      padding: 16px 20px;
      text-align: center;
      margin-bottom: 20px;
    }
    .reg-code .reg-code-label {
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: #888;
      margin: 0 0 8px;
    }
    .reg-code .reg-code-value {
      font-size: 24px;
      font-weight: 700;
      color: #1976d2;
      font-family: 'Courier New', monospace;
      letter-spacing: 3px;
      margin: 0;
    }


    /* ══════════════════════════════════════════════════════════
       GDPR FOOTER  (inside content templates)
       ══════════════════════════════════════════════════════════ */
    hr.divider {
      border: none;
      border-top: 1px solid #eee;
      margin: 0 0 16px;
    }
    p.gdpr {
      font-size: 11px;
      color: #bbb;
      text-align: center;
      margin: 0 0 28px;
    }
    p.gdpr a { color: #bbb; }


    /* ══════════════════════════════════════════════════════════
       INLINE CODE  (e.g. temporary password)
       ══════════════════════════════════════════════════════════ */
    code {
      font-family: 'Courier New', monospace;
      background: #f5f7fa;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 14px;
      color: #222;
    }


    /* ══════════════════════════════════════════════════════════
       CTA BUTTON  (newsletter register button — alias of .btn-primary)
       ══════════════════════════════════════════════════════════ */
    .cta-btn {
      display: inline-block;
      background: #1976d2;
      color: #fff !important;
      text-decoration: none !important;
      padding: 11px 28px;
      border-radius: 5px;
      font-size: 15px;
      font-weight: 700;
    }
  </style>
</head>
<body>
<table width="100%" cellpadding="0" cellspacing="0" role="presentation"
       style="background-color: #f4f4f4; width: 100%;">
  <tr>
    <td>
      <div class="email-container">
        <div class="header">
          <img src="{{logoUrl}}" alt="BATbern Logo" width="180" style="width:180px; max-width:180px; height:auto; border:0; display:block; margin:0 auto;" onerror="this.style.display='none'">
          <h1>BATbern</h1>
        </div>
        <div class="content">
          {{content}}
        </div>
        <div class="footer">
          &copy; {{currentYear}} BATbern. All rights reserved.
        </div>
      </div>
    </td>
  </tr>
</table>
</body>
</html>$tpl$, updated_at = now()
WHERE template_key = 'batbern-default' AND locale = 'en' AND is_system_template = true;

-- ── Rewritten content templates ──────────────────────────────
UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · {{eventType}}</p>
  <h2>Die Folien sind online</h2>
  <p class="sub">BATbern{{eventNumber}} · {{eventTitle}}</p>
</div>

<p>Vielen Dank, dass Sie am <strong>{{eventTitle}}</strong> vom {{eventDate}} dabei waren.</p>

<p>Die Präsentationsfolien sind jetzt online verfügbar. Sehen Sie sich einen Vortrag noch einmal an, teilen Sie ihn mit Kolleginnen und Kollegen oder holen Sie nach, was Sie verpasst haben — alles ist auf der Event-Seite versammelt.</p>

<div class="cta-box">
  <p><strong>📂 Folien ansehen</strong></p>
  <p class="muted">Alle Präsentationen des Events an einem Ort.</p>
  <a href="{{eventDetailLink}}" class="btn btn-primary">Zur Event-Seite →</a>
  <p class="link-fallback">Oder kopieren Sie diesen Link in Ihren Browser: <a href="{{eventDetailLink}}">{{eventDetailLink}}</a></p>
</div>

<p>Wir freuen uns, Sie an einem kommenden BAT wiederzusehen.<br><span class="muted">Das BATbern-Organisationskomitee</span></p>

<hr class="divider">
<p class="gdpr">Sie erhalten diese einmalige E-Mail, weil Sie sich für {{eventTitle}} angemeldet haben.</p>$tpl$, updated_at = now()
WHERE template_key = 'slides-online' AND locale = 'de' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · {{eventType}}</p>
  <h2>The slides are online</h2>
  <p class="sub">BATbern{{eventNumber}} · {{eventTitle}}</p>
</div>

<p>Thank you for joining us at <strong>{{eventTitle}}</strong> on {{eventDate}}.</p>

<p>The presentation slides are now available online. Revisit a talk, share it with a colleague, or catch up on anything you missed — everything is collected on the event page.</p>

<div class="cta-box">
  <p><strong>📂 View the slides</strong></p>
  <p class="muted">All presentations from the event, in one place.</p>
  <a href="{{eventDetailLink}}" class="btn btn-primary">Open the event page →</a>
  <p class="link-fallback">Or paste this link into your browser: <a href="{{eventDetailLink}}">{{eventDetailLink}}</a></p>
</div>

<p>We hope to see you again at a future BAT.<br><span class="muted">The BATbern Organising Committee</span></p>

<hr class="divider">
<p class="gdpr">You are receiving this one-time email because you registered for {{eventTitle}}.</p>$tpl$, updated_at = now()
WHERE template_key = 'slides-online' AND locale = 'en' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · Q&amp;A</p>
  <h2>{{#isSingle}}Eine neue Frage in Ihrer Q&amp;A{{/isSingle}}{{#isMultiple}}{{count}} neue Fragen in Ihrer Q&amp;A{{/isMultiple}}</h2>
  <p class="sub">{{sessionTitle}} · {{eventTitle}} (BATbern #{{eventNumber}})</p>
</div>

<p>Hallo {{recipientName}}</p>

<p>In der Q&amp;A zu Ihrer Session <strong>{{sessionTitle}}</strong> {{#isSingle}}ist eine neue Frage aus dem Publikum eingegangen.{{/isSingle}}{{#isMultiple}}sind {{count}} neue Fragen aus dem Publikum eingegangen.{{/isMultiple}} Sie können direkt darauf antworten.</p>

<div class="cta-box">
  <p><strong>💬 Zur Q&amp;A</strong></p>
  <p class="muted">Fragen ansehen und beantworten.</p>
  <a href="{{qnaLink}}" class="btn btn-primary">Q&amp;A öffnen →</a>
  <p class="link-fallback">Oder kopieren Sie diesen Link in Ihren Browser: <a href="{{qnaLink}}">{{qnaLink}}</a></p>
</div>

<p>Danke, dass Sie Ihr Wissen mit der BAT-Community teilen.<br><span class="muted">Das BATbern-Organisationskomitee</span></p>

<hr class="divider">
<p class="gdpr">Sie erhalten diese E-Mail als Referent:in oder Moderator:in dieser Session. Die Häufigkeit dieser Benachrichtigungen können Sie in Ihren Konto-Einstellungen anpassen.</p>$tpl$, updated_at = now()
WHERE template_key = 'qna-new-questions' AND locale = 'de' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · Q&amp;A</p>
  <h2>{{#isSingle}}A new question in your Q&amp;A{{/isSingle}}{{#isMultiple}}{{count}} new questions in your Q&amp;A{{/isMultiple}}</h2>
  <p class="sub">{{sessionTitle}} · {{eventTitle}} (BATbern #{{eventNumber}})</p>
</div>

<p>Hi {{recipientName}}</p>

<p>The Q&amp;A for your session <strong>{{sessionTitle}}</strong> has {{#isSingle}}a new question from the audience.{{/isSingle}}{{#isMultiple}}{{count}} new questions from the audience.{{/isMultiple}} You can reply to them directly.</p>

<div class="cta-box">
  <p><strong>💬 Go to the Q&amp;A</strong></p>
  <p class="muted">View the questions and answer them.</p>
  <a href="{{qnaLink}}" class="btn btn-primary">Open the Q&amp;A →</a>
  <p class="link-fallback">Or copy this link into your browser: <a href="{{qnaLink}}">{{qnaLink}}</a></p>
</div>

<p>Thank you for sharing your knowledge with the BAT community.<br><span class="muted">The BATbern organising committee</span></p>

<hr class="divider">
<p class="gdpr">You receive this email as a speaker or moderator of this session. You can adjust how often you get these notifications in your account settings.</p>$tpl$, updated_at = now()
WHERE template_key = 'qna-new-questions' AND locale = 'en' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · {{eventType}}</p>
  <h2>Sind Sie dabei?</h2>
  <p class="sub">BATbern{{eventNumber}} · {{eventTitle}}</p>
</div>

<p>Liebe / Lieber {{recipientName}},</p>

<p>das <strong>{{eventTitle}}</strong> vom {{eventDate}} ist <strong>vollständig ausgebucht</strong> — und es stehen Personen auf der Warteliste, die gerne dabei wären.</p>

<p>Falls Sie <strong>doch nicht teilnehmen</strong> können, würden wir uns sehr freuen, wenn Sie Ihre Anmeldung stornieren. So wird Ihr Platz frei und jemand von der Warteliste kann nachrücken. Ein Klick genügt:</p>

<div class="cta-box">
  <p><strong>✕ Ich kann nicht teilnehmen</strong></p>
  <p class="muted">Ihre Anmeldung wird sofort storniert — kein Login nötig.</p>
  <a href="{{deregistrationUrl}}" class="btn btn-primary">Anmeldung stornieren →</a>
  <p class="link-fallback">Oder kopieren Sie diesen Link in Ihren Browser: <a href="{{deregistrationUrl}}">{{deregistrationUrl}}</a></p>
</div>

<p>Wenn Sie dabei sind: perfekt — Sie müssen nichts tun. Wir freuen uns auf Sie!<br><span class="muted">Das BATbern-Organisationskomitee</span></p>

<hr class="divider">
<p class="gdpr">Sie erhalten diese einmalige E-Mail, weil Sie sich für {{eventTitle}} angemeldet haben.</p>$tpl$, updated_at = now()
WHERE template_key = 'registrant-notice-deregistration-call' AND locale = 'de' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · {{eventType}}</p>
  <h2>Will you be there?</h2>
  <p class="sub">BATbern{{eventNumber}} · {{eventTitle}}</p>
</div>

<p>Dear {{recipientName}},</p>

<p><strong>{{eventTitle}}</strong> on {{eventDate}} is <strong>fully booked</strong> — and there are people on the waitlist who would love to join.</p>

<p>If you find you <strong>can no longer attend</strong>, we would be very grateful if you cancelled your registration. That frees up your seat so someone from the waitlist can take it. One click is all it takes:</p>

<div class="cta-box">
  <p><strong>✕ I can’t make it</strong></p>
  <p class="muted">Your registration is cancelled immediately — no login required.</p>
  <a href="{{deregistrationUrl}}" class="btn btn-primary">Cancel my registration →</a>
  <p class="link-fallback">Or copy this link into your browser: <a href="{{deregistrationUrl}}">{{deregistrationUrl}}</a></p>
</div>

<p>If you’re coming: great — there’s nothing to do. We look forward to seeing you!<br><span class="muted">The BATbern Organising Committee</span></p>

<hr class="divider">
<p class="gdpr">You are receiving this one-off email because you registered for {{eventTitle}}.</p>$tpl$, updated_at = now()
WHERE template_key = 'registrant-notice-deregistration-call' AND locale = 'en' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · {{eventType}}</p>
  <h2>Sind Sie dabei?</h2>
  <p class="sub">BATbern{{eventNumber}} · {{eventTitle}}</p>
</div>

<p>Liebe Teilnehmerin, lieber Teilnehmer,</p>

<p>das <strong>{{eventTitle}}</strong> vom {{eventDate}} ist <strong>vollständig ausgebucht</strong> — und es stehen Personen auf der Warteliste, die gerne dabei wären.</p>

<p>Falls Sie <strong>doch nicht teilnehmen</strong> können, würden wir uns sehr freuen, wenn Sie Ihre Anmeldung stornieren. So wird Ihr Platz frei und jemand von der Warteliste kann nachrücken.</p>

<div class="cta-box">
  <p><strong>✕ So melden Sie sich ab</strong></p>
  <p class="muted">In zwei Schritten — ganz ohne Login:</p>
  <ol>
    <li>Öffnen Sie die Event-Seite und klicken Sie auf <strong>«Anmeldung stornieren»</strong>.</li>
    <li>Geben Sie Ihre E-Mail-Adresse ein — wir senden Ihnen einen Bestätigungs-Link, mit dem Sie die Abmeldung mit einem Klick abschliessen.</li>
  </ol>
  <a href="{{eventUrl}}" class="btn btn-primary">Zur Event-Seite →</a>
  <p class="link-fallback">Oder kopieren Sie diesen Link in Ihren Browser: <a href="{{eventUrl}}">{{eventUrl}}</a></p>
</div>

<p>Wenn Sie dabei sind: perfekt — Sie müssen nichts tun. Wir freuen uns auf Sie!<br><span class="muted">Das BATbern-Organisationskomitee</span></p>

<hr class="divider">
<p class="gdpr">Sie erhalten diese einmalige E-Mail, weil Sie sich für {{eventTitle}} angemeldet haben.</p>$tpl$, updated_at = now()
WHERE template_key = 'registrant-notice-deregistration-call-selfservice' AND locale = 'de' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<div class="hero">
  <p class="lbl lbl-blue">Berner Architekten Treffen · {{eventType}}</p>
  <h2>Will you be there?</h2>
  <p class="sub">BATbern{{eventNumber}} · {{eventTitle}}</p>
</div>

<p>Dear participant,</p>

<p><strong>{{eventTitle}}</strong> on {{eventDate}} is <strong>fully booked</strong> — and there are people on the waitlist who would love to join.</p>

<p>If you find you <strong>can no longer attend</strong>, we would be very grateful if you cancelled your registration. That frees up your seat so someone from the waitlist can take it.</p>

<div class="cta-box">
  <p><strong>✕ How to cancel</strong></p>
  <p class="muted">Two steps — no login required:</p>
  <ol>
    <li>Open the event page and click <strong>“Cancel my registration”</strong>.</li>
    <li>Enter your email address — we’ll send you a confirmation link that completes the cancellation in one click.</li>
  </ol>
  <a href="{{eventUrl}}" class="btn btn-primary">Go to the event page →</a>
  <p class="link-fallback">Or copy this link into your browser: <a href="{{eventUrl}}">{{eventUrl}}</a></p>
</div>

<p>If you’re coming: great — there’s nothing to do. We look forward to seeing you!<br><span class="muted">The BATbern Organising Committee</span></p>

<hr class="divider">
<p class="gdpr">You are receiving this one-off email because you registered for {{eventTitle}}.</p>$tpl$, updated_at = now()
WHERE template_key = 'registrant-notice-deregistration-call-selfservice' AND locale = 'en' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<h2>Einladung als Referent</h2>

<p>Guten Tag {{speakerName}},</p>

<p>Wir freuen uns, Sie als Referent zu unserem kommenden Berner Architekten Treffen einzuladen:</p>

<div class="event-card">
  <h3>{{eventTitle}}</h3>
  <table class="detail-table">
    <tr><td class="detail-label">Datum</td><td class="detail-value">{{eventDate}}</td></tr>
    <tr><td class="detail-label">Uhrzeit</td><td class="detail-value">{{eventTime}}</td></tr>
    <tr><td class="detail-label">Ort</td><td class="detail-value">{{venueName}}<br>{{venueAddress}}</td></tr>
  </table>
</div>

{{#sessionTitle}}
<div class="session-card">
  <h3>Vorgesehenes Thema</h3>
  <p><strong>{{sessionTitle}}</strong></p>
  {{#sessionDescription}}<p>{{sessionDescription}}</p>{{/sessionDescription}}
</div>
{{/sessionTitle}}

<p>Wir würden uns sehr freuen, Sie als Referenten bei unserem Event begrüssen zu dürfen. Ihre Expertise und Ihr Wissen wären eine wertvolle Bereicherung für unser Publikum.</p>

<div class="portal-section">
  <h3>Ihr Referenten-Portal-Konto</h3>
  <p>Um diese Einladung zu beantworten und Ihre Session-Unterlagen zu verwalten, melden Sie sich bitte im Referenten-Portal an:</p>
  <a href="{{loginUrl}}" class="portal-link">Zum Referenten-Portal anmelden</a>
  <p>Benutzername: <strong>{{usernameForLogin}}</strong></p>
  {{#temporaryPassword}}
  <p>Temporäres Passwort: <code>{{temporaryPassword}}</code></p>
  <p class="muted">Bei der ersten Anmeldung werden Sie aufgefordert, ein eigenes Passwort festzulegen. Das temporäre Passwort ist {{tempPasswordValidityDays}} Tage gültig.</p>
  {{/temporaryPassword}}
  {{#useExistingPassword}}
  <p class="muted">Sie verfügen bereits über ein BATbern-Konto. Bitte melden Sie sich mit Ihrem bestehenden Passwort an oder verwenden Sie die Funktion "Passwort vergessen" auf der Anmeldeseite, falls Sie es zurücksetzen möchten.</p>
  {{/useExistingPassword}}
  <p class="link-fallback">Anmelde-URL: <a href="{{loginUrl}}">{{loginUrl}}</a></p>
</div>

{{#responseDeadline}}
<div class="deadline-box">
  <h3>Bitte antworten Sie bis</h3>
  <p class="deadline-date">{{responseDeadline}}</p>
  {{#contentDeadline}}<p class="muted">Falls Sie zusagen, bitten wir Sie, Ihre Präsentationsunterlagen bis zum <strong>{{contentDeadline}}</strong> einzureichen.</p>{{/contentDeadline}}
</div>
{{/responseDeadline}}

<div class="contact-block">
  <h3>Fragen?</h3>
  <p><strong>{{organizerName}}</strong><br><a href="mailto:{{organizerEmail}}">{{organizerEmail}}</a></p>
</div>

<p>Wir freuen uns auf Ihre Rückmeldung!</p>

<p>Mit freundlichen Grüssen,<br><strong>Das BATbern Team</strong></p>$tpl$, updated_at = now()
WHERE template_key = 'speaker-invitation' AND locale = 'de' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<h2>Speaker Invitation</h2>

<p>Dear {{speakerName}},</p>

<p>We are pleased to invite you as a speaker at our upcoming Berner Architekten Treffen event:</p>

<div class="event-card">
  <h3>{{eventTitle}}</h3>
  <table class="detail-table">
    <tr><td class="detail-label">Date</td><td class="detail-value">{{eventDate}}</td></tr>
    <tr><td class="detail-label">Time</td><td class="detail-value">{{eventTime}}</td></tr>
    <tr><td class="detail-label">Venue</td><td class="detail-value">{{venueName}}<br>{{venueAddress}}</td></tr>
  </table>
</div>

{{#sessionTitle}}
<div class="session-card">
  <h3>Proposed Topic</h3>
  <p><strong>{{sessionTitle}}</strong></p>
  {{#sessionDescription}}<p>{{sessionDescription}}</p>{{/sessionDescription}}
</div>
{{/sessionTitle}}

<p>We would be honoured to have you as a speaker at our event. Your expertise and knowledge would be a valuable addition for our audience.</p>

<div class="portal-section">
  <h3>Your speaker portal account</h3>
  <p>To respond to this invitation and manage your session materials, please log in to the speaker portal:</p>
  <a href="{{loginUrl}}" class="portal-link">Log in to Speaker Portal</a>
  <p>Username: <strong>{{usernameForLogin}}</strong></p>
  {{#temporaryPassword}}
  <p>Temporary password: <code>{{temporaryPassword}}</code></p>
  <p class="muted">You will be asked to set your own password on first login. The temporary password is valid for {{tempPasswordValidityDays}} days.</p>
  {{/temporaryPassword}}
  {{#useExistingPassword}}
  <p class="muted">You already have a BATbern account. Please log in with your existing password, or use the "Forgot password" link on the login page if you need to reset it.</p>
  {{/useExistingPassword}}
  <p class="link-fallback">Login URL: <a href="{{loginUrl}}">{{loginUrl}}</a></p>
</div>

{{#responseDeadline}}
<div class="deadline-box">
  <h3>Please respond by</h3>
  <p class="deadline-date">{{responseDeadline}}</p>
  {{#contentDeadline}}<p class="muted">If you accept, we kindly ask you to submit your presentation materials by <strong>{{contentDeadline}}</strong>.</p>{{/contentDeadline}}
</div>
{{/responseDeadline}}

<div class="contact-block">
  <h3>Questions?</h3>
  <p><strong>{{organizerName}}</strong><br><a href="mailto:{{organizerEmail}}">{{organizerEmail}}</a></p>
</div>

<p>We look forward to hearing from you!</p>

<p>Best regards,<br><strong>The BATbern Team</strong></p>$tpl$, updated_at = now()
WHERE template_key = 'speaker-invitation' AND locale = 'en' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<h2>🎟️ Sie sind angemeldet</h2>

<p>Hallo {{attendeeFirstName}} {{attendeeLastName}},</p>

<p>das BATbern-Organisationsteam hat Sie für die folgende Veranstaltung angemeldet. <strong>Ihr Platz ist bestätigt</strong> – Sie müssen nichts weiter tun.</p>

<div class="event-card">
  <h3>{{eventTitle}}</h3>
  <table class="detail-table">
    <tr><td class="detail-label">Datum</td><td class="detail-value">{{eventDate}}</td></tr>
    <tr><td class="detail-label">Uhrzeit</td><td class="detail-value">{{eventTime}}</td></tr>
    <tr><td class="detail-label">Ort</td><td class="detail-value">{{venueName}}<br>{{venueAddress}}</td></tr>
  </table>
</div>

<a href="{{eventUrl}}" class="btn btn-primary">Zur Event-Seite</a>

<p>Ein Kalendereintrag (.ics Datei) ist dieser E-Mail als Anhang beigefügt.</p>

<p>Wir freuen uns darauf, Sie am Event zu sehen!</p>

<p>Mit freundlichen Grüssen,<br><strong>Das BATbern Team</strong></p>

<hr class="divider">
<p class="gdpr">Sie können leider doch nicht teilnehmen? <a href="{{deregistrationUrl}}">Hier abmelden</a></p>$tpl$, updated_at = now()
WHERE template_key = 'registration-organizer-added' AND locale = 'de' AND is_system_template = true;

UPDATE email_templates SET html_body = $tpl$<h2>🎟️ You're registered</h2>

<p>Hello {{attendeeFirstName}} {{attendeeLastName}},</p>

<p>the BATbern organizing team has registered you for the following event. <strong>Your place is confirmed</strong> — there's nothing more you need to do.</p>

<div class="event-card">
  <h3>{{eventTitle}}</h3>
  <table class="detail-table">
    <tr><td class="detail-label">Date</td><td class="detail-value">{{eventDate}}</td></tr>
    <tr><td class="detail-label">Time</td><td class="detail-value">{{eventTime}}</td></tr>
    <tr><td class="detail-label">Venue</td><td class="detail-value">{{venueName}}<br>{{venueAddress}}</td></tr>
  </table>
</div>

<a href="{{eventUrl}}" class="btn btn-primary">View the event page</a>

<p>A calendar entry (.ics file) is attached to this email.</p>

<p>We look forward to seeing you at the event!</p>

<p>Kind regards,<br><strong>The BATbern Team</strong></p>

<hr class="divider">
<p class="gdpr">Can't make it after all? <a href="{{deregistrationUrl}}">Cancel your registration here</a></p>$tpl$, updated_at = now()
WHERE template_key = 'registration-organizer-added' AND locale = 'en' AND is_system_template = true;

