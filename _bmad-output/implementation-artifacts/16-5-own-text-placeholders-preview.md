# Story 16.5: Own text with placeholders and per-recipient preview

Status: ready-for-dev

## Story

As an **organizer**,
I want **to write my own subject and text for any audience, insert placeholders such as the speaker's name with one click, and see the result for each real recipient before sending**,
so that **I can send a one-off message (a room change, a personal note to all speakers) without asking someone to create a template first, and without a broken `{{deadlin}}` reaching anyone**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.5**, decision 5 (Mustache placeholders, one render path) and the 16.8 decision on the registrant one-shot guard.
> **Depends on 16.8** (built inside the shared composer, once, not per audience) and, for the Partners chips, on 16.9. Do not start before 16.8a and 16.8b are on `develop`.
> **UX reference:** `docs/ux/mail-center-spec.md` → "Composer" steps 1–3, 5 and 7 ("Als Vorlage speichern").

## Acceptance Criteria

1. **AC1 (preview of a real recipient)** — GIVEN own text with `{{speakerName}}`, WHEN previewing recipient 3 of 8, THEN the preview shows that speaker's name, highlighted.
2. **AC2 (unknown placeholder)** — GIVEN own text with `{{deadlin}}`, THEN the preview marks it red with a message, both send buttons are disabled, and a direct `POST /events/{code}/mails` (either target) is rejected with 400 naming the placeholder. The same holds for `{{#name}}`/`{{/name}}` blocks and for the subject.
3. **AC3 (newsletter unsubscribe)** — GIVEN a newsletter in own text without `{{unsubscribeLink}}`, THEN every sent mail contains a working unsubscribe link for its recipient (the standard DE/EN footer is appended). A test send carries the dummy link (16.8 AC5).
4. **AC4 (byte-equal)** — The body and subject recorded by 16.1a for recipient *i* equal the preview of recipient *i* for own text, byte for byte (after redaction), for each audience.
5. **AC5 (marked as own text)** — "What went out" shows the send as "own text" (`content_source = OWN_TEXT`, `template_key` null). No UI change needed beyond what 16.3 shows; covered by a test.
6. **AC6 (chips)** — The chips above the body are exactly the audience's offered variables (list in Dev Notes), with plain labels ("Name", "Deadline", "Talk title" …). A click inserts `{{var}}` at the cursor of the field that last had focus (subject or body). Audiences without personal fields say so.
7. **AC7 (starting point)** — "Use this template as a starting point" copies the selected template's subject and body (current language) into own text; the template is unchanged. Not offered for the partner meeting invite (16.9: it has no stored template).
8. **AC8 (save as template)** — "Save as template" (own text only) stores subject + body as a new non-system template through the existing `POST /email-templates`, with the audience's category, the chosen language and the BATbern layout. It then appears in that audience's template list. A key clash shows the 409 inline.
9. **AC9 (registrant one-shot guard)** — GIVEN an own-text registrant mail with subject S was sent for event E, WHEN an organizer sends own text with subject S to E again, THEN the composer asks "Already sent on {date}. Send again?" and only an explicit "Send again" (request flag `confirmResend = true`) sends it. A send in progress is always 409. Different subject → no prompt.
10. **AC10 (same guards)** — Own text goes through the same render service, the same ADR-015 walls (`EmailService.assertNotTestMarked`, newsletter test-event wall) and the same confirm step as templates. The backend validates placeholders on preview and on send; the frontend check is convenience only.
11. **AC11 (recipients for own text)** — Speakers: own text needs a status selection (Invited, Accepted, Content submitted, Quality reviewed; at least one), shown as chips; the count updates from the server. Other audiences use their 16.8 recipients unchanged.
12. **AC12 (new variable `sessionTitle`)** — Speakers get `sessionTitle` (the title of the speaker's session, empty when none) in own text **and** in the six reminder templates' variable map.
13. **AC13 (input limits)** — Subject: 1–500 characters, no line breaks (400 otherwise). Body: 1 character to 200 KB. Empty own text cannot be previewed.
14. **AC14 (i18n)** — New UI keys (toggle, chips labels, starting point, save as template, guard prompt, errors) in all 10 locales, EN + DE first-class. The appended newsletter footer exists in DE and EN only (email content rule).
15. **AC15 (mail safety)** — No test sends real mail; send checks on local dev only (`/dev/emails`).

## Tasks / Subtasks

- [ ] **Task 1 — Contract first** (AC: 2, 9, 11, 13)
  - [ ] `event-mail-api.openapi.yml`: `MailComposeRequest` gets `ownSubject`, `ownBodyHtml`, `speakerStatuses[]`, `confirmResend`; `contentSource: OWN_TEXT` becomes valid; `MailPreviewResponse` gets `highlightedHtml`, `highlightedSubject`, `offeredVariables[]` (`name`), `alreadySent` (`{sentAt, mailKey}` or null, registrants only). New 400 error code `UNKNOWN_PLACEHOLDER` with `placeholders[]` in the error details; 409 `ALREADY_SENT` with `sentAt`.
  - [ ] Regenerate frontend types.
- [ ] **Task 2 — Migration** (AC: 3, 9)
  - [ ] `V{next}__newsletter_sends_own_text.sql` in EMS: `newsletter_sends` gets `content_source VARCHAR(10) NOT NULL DEFAULT 'TEMPLATE'` (check `TEMPLATE|OWN_TEXT`), `own_subject TEXT`, `own_body_html TEXT`; index `(event_id, own_subject)` where `content_source = 'OWN_TEXT'`. Own-text rows use `template_key = 'own-text'` (the column is `NOT NULL`, `NewsletterSend.java:44`). **Re-check the EMS head before numbering**: V136 at drafting time; 16.2 (inbound) adds migrations too, whichever ships first takes the number.
  - [ ] Why the columns: the newsletter and registrant sends are async jobs and the newsletter retry re-renders from the stored source (`retryFailedRecipients`, `NewsletterEmailService` :523-640). Own text needs its source stored next to `template_key`.
- [ ] **Task 3 — RED: backend tests** (AC: 1–5, 9–13, 15)
  - [ ] `PlaceholderValidatorTest`: allowed, unknown, block tags, subject, whitespace variants (`{{ speakerName }}` counts as unknown: `replaceVariables` matches the exact `{{name}}`).
  - [ ] `PreviewHighlighterTest`: value in text → `<mark>`; value inside an attribute (`href="{{deregistrationUrl}}"`) → not wrapped, HTML stays valid; unknown placeholder → `<mark class="unknown">`.
  - [ ] `OwnTextComposerIntegrationTest` per audience: preview *i*, send, byte-equal record (AC4), `content_source = OWN_TEXT`; 400 on unknown placeholder for preview-less direct send (AC2); newsletter footer appended and per-recipient unsubscribe link present (AC3); registrant guard prompt, `confirmResend`, 409 while running (AC9); subject with CR/LF → 400 (AC13).
  - [ ] Newsletter retry of an own-text send re-renders from `own_body_html` (not from a template).
  - [ ] `SpeakerReminderEmailServiceTest`: `sessionTitle` in the variables (AC12).
- [ ] **Task 4 — Validation + render** (AC: 1, 2, 4, 10, 13)
  - [ ] `PlaceholderValidator` (`mailcenter.compose`): names in subject + body must be in `adapter.allowedVariables()` ∪ layout variables (`currentYear`, `logoUrl`, `content`). Runs in `MailComposerService` before preview and send.
  - [ ] `OwnTextSource` implements 16.8's `TemplateSource`: subject + body from the request, layout `batbern-default`, chosen locale. `MailRenderer` is unchanged; this is the point of decision 5.
  - [ ] `PreviewHighlighter`: second render with sentinel-wrapped values; outside tags → `<mark data-var="name">`, inside tags → sentinels stripped. Only `html`/`subject` (unhighlighted) are compared byte-equal and sent.
  - [ ] `MailContext` for own text: `contentSource = OWN_TEXT`, `templateKey = null` (group id gets `own-text`, `MailContext.of` :60-65 handles null). Add `MailContext.withOwnText()` in shared kernel (+ unit test) rather than calling the canonical constructor in EMS.
- [ ] **Task 5 — Adapters** (AC: 3, 9, 11, 12)
  - [ ] `allowedVariables()` / `offeredVariables()` per adapter: offered = the chip list in Dev Notes; allowed = offered ∪ the rest of today's map (so a starting-point copy of a template that uses e.g. `speakersSection` still validates).
  - [ ] Newsletter: own text stored on the `newsletter_sends` row; the job and retry render from it; footer appended when the body lacks `{{unsubscribeLink}}` — for own text **and** for templates saved from own text (rule: any newsletter content without `{{unsubscribeLink}}` gets the footer). Footer DE/EN text as constants in the adapter, not as a seeded template (a `newsletter-*` file would be seeded as a sendable NEWSLETTER template by `EmailTemplateSeedService.deriveCategory` :177).
  - [ ] Registrants: own text stored on the `newsletter_sends` row; guard query `findFirstByEventIdAndContentSourceAndOwnSubjectAndStatusIn(...)`; registrants still get the layout in their own language, the own text itself is in the language the organizer wrote it (Dev Notes).
  - [ ] Speakers: recipients by `speakerStatuses`; delivery through a new method on `SpeakerReminderEmailService` or a small `SpeakerOwnTextMailService` (must pass the 16.1a inventory guard: it calls `emailService.send*` with a `MailContext`), Reply-To moderator, CC additional emails as reminders do; outreach history row per speaker (`manual_email`, note "Own text: {subject}"), **no** reminder-log row and **no** escalation. `sessionTitle` from `SessionRepository` via `speaker_pool.session_id`.
  - [ ] Venue: one mail as today; `notes` stays available as a variable.
  - [ ] Partners (if 16.9 is live): offered variables from 16.9's adapter.
- [ ] **Task 6 — RED: frontend tests** (AC: 1, 2, 6–9, 11)
  - [ ] `MailComposer.test.tsx` (MSW): toggle; chips insert at cursor in subject and in body; red unknown placeholder disables both buttons; starting point copies and leaves the template; save as template → POST `/email-templates` with category; 409 key clash inline; registrant "already sent" prompt → second request with `confirmResend: true`; speaker status chips.
- [ ] **Task 7 — Frontend** (AC: 6–9, 11, 14)
  - [ ] `composer/OwnTextEditor.tsx`: subject `TextField` + body editor. Reuse the TinyMCE setup of `Admin/EmailTemplateEditModal.tsx` (same plugins, lazy-loaded, organizer area only); chip insertion via `editor.insertContent('{{var}}')`, subject via selection range.
  - [ ] `composer/PlaceholderChips.tsx`: labels from `eventPage.mail.composer.variables.{name}`; list from `offeredVariables` of the preview response (backend is the single source).
  - [ ] Starting point: `GET /email-templates/{key}/{locale}` through the existing service; save as template: existing `createEmailTemplate` service, key input with the spec's kebab-case pattern.
  - [ ] Preview shows `highlightedHtml` (sandboxed iframe, no scripts); the yellow `mark` and red `mark.unknown` styles are part of the preview HTML (inline `<style>` added by `PreviewHighlighter`), not of the sent mail.
  - [ ] i18n keys in all 10 locales.
- [ ] **Task 8 — Verify** (AC: all)
  - [ ] Full EMS suite and Vitest to files (`set -o pipefail; ... | tee`).
  - [ ] Local dev: own text per audience on a throwaway event; preview stepping; test send; real send; compare `/dev/emails` with the record; registrant guard prompt; newsletter footer link works on local (`/unsubscribe?token=...` resolves).
  - [ ] Playwright: extend `event-mail-composer.spec.ts` (no send) with own text + chips + red placeholder; the send spec stays `@sends-real-email`, local only.
  - [ ] Docs: user guide `docs/user-guide/features/event-mail.md` (own text, placeholders), architecture note on own-text storage.

## Dev Notes

### Offered variables (chips) per audience

| Audience | Offered (chip label) | Source today |
|---|---|---|
| Newsletter | `eventTitle`, `eventDate`, `eventTime`, `venue`, `registrationLink`, `eventDetailLink`, `eventNumber` | `NewsletterEmailService.buildVariables` :701-723. `unsubscribeLink` is not a chip; footer appended (AC3). No personal fields: "no personal fields" hint. |
| Speakers | `speakerName`, `sessionTitle` (**new**), `deadline`, `daysRemaining`, `portalLink`, `organizerName`, `organizerEmail`, `eventTitle`, `eventDate` | `SpeakerReminderEmailService` :191-202 |
| Registrants | `recipientName`, `deregistrationUrl`, `eventNumber`, `eventTitle`, `eventDate`, `eventType` | `SlidesOnlineEmailService` :405-445 (`attendeeFirstName` allowed, not offered) |
| Venue & Catering | `salutation`, `eventDate`, `topicTitle`, `agenda`, `notes`, `organizerSignature` | `VenueCoordinationService` :191-212 (`eventTitle`, `venueName` allowed, not offered). One mail for all roles: "no personal fields per recipient". |
| Partners | as defined by 16.9's adapter (proposed: `recipientName`, `companyName`, `eventTitle`, `eventDate`, `eventNumber`) | 16.9 |

`deadline` and `daysRemaining` for own text to speakers: the speaker's response deadline when the speaker is INVITED, the content deadline otherwise; event date as fallback (same fallback as `sendManualReminder`, `SpeakerReminderService` :176-184).

### How `replaceVariables` behaves (it decides the validator)

`EmailService.replaceVariables` (`shared-kernel/.../EmailService.java:657-692`) only replaces names present in the map, exact `{{name}}` text, and **leaves unknown placeholders in the output**. It does not HTML-escape values. Conditional blocks `{{#name}}…{{/name}}` are removed when the value is empty. So:
- validate before rendering (unknown names would otherwise go out literally);
- `{{ name }}` with spaces is unknown;
- values are trusted as today (names from CUMS, links built by us). Do not change escaping in this story; it would alter every existing mail.

### Own text is HTML written by an organizer

Same trust level as the admin template editor, which already stores organizer HTML (`EmailTemplateEditModal`). No sanitiser dependency in EMS (none in the build). The preview iframe stays sandboxed without scripts. Subject is plain text: reject CR/LF (header injection on the raw-MIME path, `EmailService` :360-380).

### Language

Own text is written in one language. The composer's language choice selects the layout locale and, for registrants and speakers, overrides the per-recipient language for **this** send (today both resolve it per recipient: `SlidesOnlineEmailService.resolveLocale` :452-468, `SpeakerReminderService.speakerLocale`). State this in the UI hint: "Everyone gets this text as written."

### Registrant one-shot guard

Today's guard is per event + template key, statuses COMPLETED/PARTIAL (`SlidesOnlineEmailService` :162-176), with a known read-then-act race (comment :155-161). For own text the key is event + exact subject (owner decision). The prompt comes from the preview response (`alreadySent`), so the organizer sees it before clicking send; the send endpoint enforces it again (409 `ALREADY_SENT` without `confirmResend`).

### Byte-equal and redaction

The record stores bodies after `MailRedaction` (token links, registration code). 16.8 made the preview return the redacted render, so the comparison is redacted vs redacted. Own text containing `{{deregistrationUrl}}` shows `[redacted]` in preview and record; the recipient gets the working link. Explain this in the preview hint ("Links with a personal token are hidden here").

### Rules that bite

- One render path: `MailRenderer` from 16.8. No second renderer for own text.
- ADR-008: no new endpoint in this story except fields on 16.8's; `/email-templates` POST is existing and ORGANIZER-only.
- Flyway: new migration only; never edit V135/V136.
- i18n: UI 10 locales; footer text DE + EN only.
- AWS is production: no send against staging, not even a test send to organizers from a test.
- PR = production deploy: push, ask the owner, then open.

### PR

One PR is enough (`feature/16-5-own-text`): the migration is additive, the toggle was disabled in 16.8b, so the backend and frontend can ship together. If the PR grows past review size, split backend (migration, validator, adapters; toggle still disabled) and frontend.

### References

- `docs/prd/epic-16-mail-center.md` → Story 16.5, decision 5, Story 16.8 decisions
- `_bmad-output/implementation-artifacts/16-8-shared-composer.md` (adapters, `MailRenderer`, test send, preview fields)
- `docs/ux/mail-center-spec.md` → Composer
- `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/MailContext.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/{NewsletterEmailService,SlidesOnlineEmailService,SpeakerReminderEmailService,VenueCoordinationService,EmailTemplateSeedService}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterSend.java`
- `web-frontend/src/components/organizer/Admin/EmailTemplateEditModal.tsx`, `EmailTemplatesTab.tsx`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story drafted from Epic 16 on top of the 16.8 design, grounded in code at 15a13959.
