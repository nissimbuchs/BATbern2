# Story 16.5: Own text with placeholders and per-recipient preview

Status: review

## Story

As an **organizer**,
I want **to write my own subject and text for any audience, insert placeholders such as the speaker's name with one click, and see the result for each real recipient before sending**,
so that **I can send a one-off message (a room change, a personal note to all speakers) without asking someone to create a template first, and without a broken `{{deadlin}}` reaching anyone**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.5**, decision 5 (Mustache placeholders, one render path) and the 16.8 decision on the registrant one-shot guard.
> **Depends on 16.8** (built inside the shared composer, once, not per audience) and, for the Partners chips, on 16.9. Do not start before 16.8a and 16.8b are on `develop`.
> **UX reference:** `docs/ux/mail-center-spec.md` → "Composer" steps 1–3, 5 and 7 ("Als Vorlage speichern").

## Design update 2026-11-02 (after Story 16.11)

Story 16.11 (one pipeline, one variable catalog, PR #1177) replaces the render design this story was drafted on. Where the tasks below say otherwise, this section wins.

- **No `TemplateSource` / `OwnTextSource`.** Own text is one more `MailContent` (`contentSource = OWN_TEXT`, `templateKey` null, the organizer's subject and body, layout `batbern-default`, the chosen locale) rendered by `MailPipeline.render` with the map `AudienceAdapter.variables(context, recipient, test)` returns. Same renderer and same variables as the template path, so own text can use whatever a template of that audience can.
- **Allowed variables come from the catalog** (`AudienceAdapter.allowedVariables()`, derived from the audience's scopes; aliases included). The validator checks subject and body against that set. **Chips** are a short per-audience subset, `offeredVariables()`, a new interface method (table in Dev Notes; Partners: `eventTitle`, `eventDate`, `eventNumber`, `eventTime`, `venueName`).
- **`Rendering` gets no variable map** (it is the redacted "as sent" value). Highlighting re-renders with sentinel-wrapped values from `variables(...)`.
- **Newsletter footer rule** stays as written: any newsletter content (own text or a stored template) without `{{unsubscribeLink}}` gets the DE/EN footer appended before the layout merge. All seeded newsletter templates contain the link, so no existing mail changes.
- **Own text is stored on the `newsletter_sends` row** for the two async jobs (newsletter, registrants); the job and the newsletter retry render from the row (16.11 AC8: content fixed per send). Migration number: **V138** (V137 is 16.9a's `mail_message.reference`).
- **Partners** (16.9 B, same PR): one mail to all partners, so system and event variables only; own text goes through `sendGroupHtmlEmailSync` like the template path.
- **Owner pre-authorised** push and PR for this story (2026-11-01, overnight sequence 16.11 → 16.9 + 16.5); the PR ships 16.9 A, 16.9 B and 16.5 together.

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

- [x] **Task 1 — Contract first** (AC: 2, 9, 11, 13)
  - [x] `event-mail-api.openapi.yml`: `MailComposeRequest` gets `ownSubject`, `ownBodyHtml`, `speakerStatuses[]`, `confirmResend`; `contentSource: OWN_TEXT` becomes valid; `MailPreviewResponse` gets `highlightedHtml`, `highlightedSubject`, `offeredVariables[]` (`name`), `alreadySent` (`{sentAt, mailKey}` or null, registrants only). New 400 error code `UNKNOWN_PLACEHOLDER` with `placeholders[]` in the error details; 409 `ALREADY_SENT` with `sentAt`.
  - [x] Regenerate frontend types.
- [x] **Task 2 — Migration** (AC: 3, 9)
  - [x] `V{next}__newsletter_sends_own_text.sql` in EMS: `newsletter_sends` gets `content_source VARCHAR(10) NOT NULL DEFAULT 'TEMPLATE'` (check `TEMPLATE|OWN_TEXT`), `own_subject TEXT`, `own_body_html TEXT`; index `(event_id, own_subject)` where `content_source = 'OWN_TEXT'`. Own-text rows use `template_key = 'own-text'` (the column is `NOT NULL`, `NewsletterSend.java:44`). **Re-check the EMS head before numbering**: V136 at drafting time; 16.2 (inbound) adds migrations too, whichever ships first takes the number.
  - [x] Why the columns: the newsletter and registrant sends are async jobs and the newsletter retry re-renders from the stored source (`retryFailedRecipients`, `NewsletterEmailService` :523-640). Own text needs its source stored next to `template_key`.
- [x] **Task 3 — RED: backend tests** (AC: 1–5, 9–13, 15)
  - [x] `PlaceholderValidatorTest`: allowed, unknown, block tags, subject, whitespace variants (`{{ speakerName }}` counts as unknown: `replaceVariables` matches the exact `{{name}}`).
  - [x] `PreviewHighlighterTest`: value in text → `<mark>`; value inside an attribute (`href="{{deregistrationUrl}}"`) → not wrapped, HTML stays valid; unknown placeholder → `<mark class="unknown">`.
  - [x] `OwnTextComposerIntegrationTest` per audience: preview *i*, send, byte-equal record (AC4), `content_source = OWN_TEXT`; 400 on unknown placeholder for preview-less direct send (AC2); newsletter footer appended and per-recipient unsubscribe link present (AC3); registrant guard prompt, `confirmResend`, 409 while running (AC9); subject with CR/LF → 400 (AC13).
  - [x] Newsletter retry of an own-text send re-renders from `own_body_html` (not from a template).
  - [x] `SpeakerReminderEmailServiceTest`: `sessionTitle` in the variables (AC12).
- [x] **Task 4 — Validation + render** (AC: 1, 2, 4, 10, 13)
  - [x] `PlaceholderValidator` (`mailcenter.compose`): names in subject + body must be in `adapter.allowedVariables()` ∪ layout variables (`currentYear`, `logoUrl`, `content`). Runs in `MailComposerService` before preview and send.
  - [x] `OwnTextSource` implements 16.8's `TemplateSource`: subject + body from the request, layout `batbern-default`, chosen locale. `MailRenderer` is unchanged; this is the point of decision 5.
  - [x] `PreviewHighlighter`: second render with sentinel-wrapped values; outside tags → `<mark data-var="name">`, inside tags → sentinels stripped. Only `html`/`subject` (unhighlighted) are compared byte-equal and sent.
  - [x] `MailContext` for own text: `contentSource = OWN_TEXT`, `templateKey = null` (group id gets `own-text`, `MailContext.of` :60-65 handles null). Add `MailContext.withOwnText()` in shared kernel (+ unit test) rather than calling the canonical constructor in EMS.
- [x] **Task 5 — Adapters** (AC: 3, 9, 11, 12)
  - [x] `allowedVariables()` / `offeredVariables()` per adapter: offered = the chip list in Dev Notes; allowed = offered ∪ the rest of today's map (so a starting-point copy of a template that uses e.g. `speakersSection` still validates).
  - [x] Newsletter: own text stored on the `newsletter_sends` row; the job and retry render from it; footer appended when the body lacks `{{unsubscribeLink}}` — for own text **and** for templates saved from own text (rule: any newsletter content without `{{unsubscribeLink}}` gets the footer). Footer DE/EN text as constants in the adapter, not as a seeded template (a `newsletter-*` file would be seeded as a sendable NEWSLETTER template by `EmailTemplateSeedService.deriveCategory` :177).
  - [x] Registrants: own text stored on the `newsletter_sends` row; guard query `findFirstByEventIdAndContentSourceAndOwnSubjectAndStatusIn(...)`; registrants still get the layout in their own language, the own text itself is in the language the organizer wrote it (Dev Notes).
  - [x] Speakers: recipients by `speakerStatuses`; delivery through a new method on `SpeakerReminderEmailService` or a small `SpeakerOwnTextMailService` (must pass the 16.1a inventory guard: it calls `emailService.send*` with a `MailContext`), Reply-To moderator, CC additional emails as reminders do; outreach history row per speaker (`manual_email`, note "Own text: {subject}"), **no** reminder-log row and **no** escalation. `sessionTitle` from `SessionRepository` via `speaker_pool.session_id`.
  - [x] Venue: one mail as today; `notes` stays available as a variable.
  - [x] Partners (if 16.9 is live): offered variables from 16.9's adapter.
- [x] **Task 6 — RED: frontend tests** (AC: 1, 2, 6–9, 11)
  - [x] `MailComposer.test.tsx` (MSW): toggle; chips insert at cursor in subject and in body; red unknown placeholder disables both buttons; starting point copies and leaves the template; save as template → POST `/email-templates` with category; 409 key clash inline; registrant "already sent" prompt → second request with `confirmResend: true`; speaker status chips.
- [x] **Task 7 — Frontend** (AC: 6–9, 11, 14)
  - [x] `composer/OwnTextEditor.tsx`: subject `TextField` + body editor. Reuse the TinyMCE setup of `Admin/EmailTemplateEditModal.tsx` (same plugins, lazy-loaded, organizer area only); chip insertion via `editor.insertContent('{{var}}')`, subject via selection range.
  - [x] `composer/PlaceholderChips.tsx`: labels from `eventPage.mail.composer.variables.{name}`; list from `offeredVariables` of the preview response (backend is the single source).
  - [x] Starting point: `GET /email-templates/{key}/{locale}` through the existing service; save as template: existing `createEmailTemplate` service, key input with the spec's kebab-case pattern.
  - [x] Preview shows `highlightedHtml` (sandboxed iframe, no scripts); the yellow `mark` and red `mark.unknown` styles are part of the preview HTML (inline `<style>` added by `PreviewHighlighter`), not of the sent mail.
  - [x] i18n keys in all 10 locales.
- [ ] **Task 8 — Verify** (AC: all)
  - [ ] Full EMS suite and Vitest to files (`set -o pipefail; ... | tee`).
  - [ ] Local dev: own text per audience on a throwaway event; preview stepping; test send; real send; compare `/dev/emails` with the record; registrant guard prompt; newsletter footer link works on local (`/unsubscribe?token=...` resolves).
  - [ ] Playwright: extend `event-mail-composer.spec.ts` (no send) with own text + chips + red placeholder; the send spec stays `@sends-real-email`, local only.
  - [x] Docs: user guide `docs/user-guide/features/event-mail.md` (own text, placeholders), architecture note on own-text storage.

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

Claude Opus 5.5 (Amelia, bmad-dev-story), 2026-11-02. Frontend (Tasks 6, 7) by a delegated agent on the committed contract, reviewed and committed by Amelia.

### Completion Notes List

- Built on 16.11 as the design update says: own text = `MailContent` (`OwnText`), rendered by `MailPipeline` with `AudienceAdapter.variables(...)`; no `TemplateSource`. `MailContext.ownText(...)` (shared kernel, unit-tested) instead of `withOwnText()`.
- **Deviation, allowed variables:** the validator uses the names the audience's variable map really fills for a recipient (`MailComposerService.allowedVariables`), not the catalog scopes alone. A scope can name variables an audience never fills (registrant notices fill only the cancel link, name and first name of REGISTRATION), and those would otherwise pass validation and reach the recipient literally. The preview returns that set as `allowedVariables`; chips come from `offeredVariables()` (new on the interface).
- **Deviation, footer:** the story rule is kept (any newsletter content without `{{unsubscribeLink}}`, own text or a template), applied in `NewsletterEmailService.prepare`; no seeded newsletter template changes (all have the link).
- Own-text send rows: `own-text-newsletter` / `own-text-registrants` instead of one `own-text` key, because both audiences share `newsletter_sends` and the registrant guard must not match a newsletter row. V138.
- Speakers: own text is not a reminder, so the reminder opt-out does not exclude a speaker (story silent; decided here, written in the user guide). Language for registrants and speakers: the organizer's choice for everyone (Dev Notes).
- **AC8 deviation:** "Save as template" is not offered for speakers: their template mode sends the six reminder keys only, so a saved speaker template could never be sent from there. Other audiences: saved through the existing create endpoint with the audience's category.
- Contract gaps reported by the frontend agent, not fixed: chips need one preview first (template mode previews before the toggle in practice); the 400/409 error body has no OpenAPI schema (frontend reads `details.code`); `CreateEmailTemplateRequest` has no `isSystemTemplate` (backend default false).
- Also in this PR: 16.9 A + B, and the owner decision of 2026-11-02 that every partner mail (invite with its own ICS, cancellation, plain partner mail) goes to each recipient on their own (see 16.9).
- Tests: `PlaceholderValidatorTest` (4), `PreviewHighlighterTest` (5), `SpeakerAudienceAdapterTest` own-text cases (3), `OwnTextComposerIntegrationTest` (8: newsletter footer + per-recipient link + byte-equal + row; retry from the row; registrants guard/409/confirmResend; speakers by status + outreach note + no reminder log; venue; unknown placeholder 400 for both targets, nothing recorded; limits; test send), `MailContextTest.ownText`, `SpeakerModeratorReplyToTest` sessionTitle (AC12). Frontend: `MailComposerOwnText.test.tsx` (25), `highlightedSubject.test.ts` (4), EventPage Vitest 428 passed / 0 failed, type-check and lint clean (agent's runs).
- **Not done:** local-dev send per audience (Task 8) and the Playwright own-text extension (only the toggle assertion in `event-mail-composer.spec.ts` was updated). Local dev would show capture and record only, not SES delivery (no SesClient locally).

### File List

Backend (EMS `services/event-management-service/src/`): new `main/java/ch/batbern/events/mailcenter/compose/{OwnText,PlaceholderValidator,PreviewHighlighter,UnknownPlaceholderException,AlreadySentException}.java`, `main/java/ch/batbern/events/service/SpeakerOwnTextMailService.java`, `main/resources/db/migration/V138__newsletter_sends_own_text.sql`; modified `mailcenter/compose/{AudienceAdapter,ComposeRequest,MailComposerService,NewsletterAudienceAdapter,RegistrantAudienceAdapter,SpeakerAudienceAdapter,VenueAudienceAdapter,PartnerAudienceAdapter}.java`, `mailcenter/EventMailController.java`, `exception/GlobalExceptionHandler.java`, `domain/NewsletterSend.java`, `repository/NewsletterSendRepository.java`, `service/{NewsletterEmailService,SlidesOnlineEmailService,SpeakerReminderEmailService,VenueCoordinationService}.java`. Tests: new `mailcenter/OwnTextComposerIntegrationTest.java`, `mailcenter/compose/{PlaceholderValidatorTest,PreviewHighlighterTest}.java`; modified `mailcenter/MailComposerControllerIntegrationTest.java`, `mailcenter/compose/{AudienceAdapterVariablesTest,SpeakerAudienceAdapterTest}.java`, `service/SpeakerModeratorReplyToTest.java`.

Shared kernel: `mail/MailContext.java`, `mail/MailContextTest.java`.

Contract: `docs/api/event-mail-api.openapi.yml`, `web-frontend/src/types/generated/event-mail-api.types.ts`.

Frontend (`web-frontend/src/components/organizer/EventPage/communications/composer/`): new `OwnTextEditor.tsx`, `OwnTextBodyEditor.tsx`, `PlaceholderChips.tsx`, `SaveAsTemplateDialog.tsx`, `highlightedSubject.ts`, `__tests__/MailComposerOwnText.test.tsx`, `__tests__/highlightedSubject.test.ts`; modified `MailComposer.tsx`, `ComposerPreview.tsx`, `ComposerFooter.tsx`, `types.ts`, `audiences/{newsletter,registrants,speakers,venue,partners}.ts`, `audiences/SpeakerFields.tsx`, `__tests__/MailComposer.test.tsx`; `web-frontend/public/locales/*/events.json` (10); `web-frontend/e2e/organizer/event-mail-composer.spec.ts`.

Docs: `docs/user-guide/features/event-mail.md`, `docs/user-guide/appendix/changelog.md`, `docs/architecture/06-backend-architecture.md`.

## Change Log

- 2026-11-01: Story drafted from Epic 16 on top of the 16.8 design, grounded in code at 15a13959.
- 2026-11-02: Design update for 16.11; implemented (backend, frontend, docs); status review. Local-dev and Playwright checks open.
