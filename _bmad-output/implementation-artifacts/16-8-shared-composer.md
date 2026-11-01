# Story 16.8: One shared composer for all audiences

Status: in-progress (16.8a and 16.8b in review; 16.8c open, after one release)

## Story

As an **organizer**,
I want **one composer that works the same for newsletter, registrants, speakers and venue: pick a template, see the mail exactly as each real recipient will get it, send a test to the organizer team, then send**,
so that **I never have to guess which of six speaker reminders goes out, or what a registrant actually reads, and every send is checked the same way**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.8** (Winston's compose review 2026-10-30) and the owner decisions recorded there and on 2026-10-31.
> Depends on 16.1a (recording), 16.3 (the `/events/{code}/mails` resource), 16.4 (the "+ New mail" picker). All are on `develop` at 15a13959.
> Followed by 16.9 (Partners adapter + partner-service recording) and 16.5 (own text, built inside this composer).
> **UX reference:** `docs/ux/mail-center-spec.md` → "Composer" (steps 1–8), layout `docs/ux/mail-center-layouts.html` (variant A, inline) and `docs/ux/mail-center-mobile.html`. Prototype: `docs/ux/mail-center-prototype.html`. Layout reference only; build with MUI.

## Size and split (read first)

This story is too large for one PR. It ships as **three parts, three PRs**, in this order. Each part is deployable on its own (a PR to `develop` deploys to production at once):

| Part | Content | Visible change | PR branch |
|---|---|---|---|
| **16.8a** | Backend: spec, `MailComposerService`, `AudienceAdapter` + four adapters, one render path, test send, progress + retry on the mail resource, `includeTests` filter. Old endpoints and old UI untouched. | None, except test sends are hidden in "What went out" by default (AC9). | `feature/16-8a-composer-api` |
| **16.8b** | Frontend: `MailComposer` + four frontend adapters replace the five compose components in the picker. Partners keeps `PartnerMeetingsCompose` (16.9 changes it). | The new composer. | `feature/16-8b-composer-ui` |
| **16.8c** | Clean-up, **after one release** with 16.8b live and no regression reported: delete old components, old endpoints, their spec entries, Bruno files, `GET /events/{code}/newsletter/history`. | None. | `feature/16-8c-composer-cleanup` |

Do not start 16.8c in the same branch as 16.8b. Wait for the owner's go after 16.8b has been in production for one release.

## Acceptance Criteria

### 16.8a — API and adapters

1. **AC1 (one resource)** — `docs/api/event-mail-api.openapi.yml` gains, on the existing `/events/{eventCode}/mails` resource, and nowhere else:
   - `POST /events/{eventCode}/mails/preview` → `MailPreviewResponse`;
   - `POST /events/{eventCode}/mails` (`target: RECIPIENTS | ORGANIZERS`) → `MailSendResponse`, status 202;
   - `POST /events/{eventCode}/mails/{mailKey}/retry` → `MailSendResponse` (newsletter retry, moved here from `/newsletter/sends/{sendId}/retry`);
   - `GET /events/{eventCode}/mails/{mailKey}` extended with a nullable `progress` object;
   - `GET /events/{eventCode}/mails` and `/counts` gain `includeTests` (default `false`).
   All new operations `@PreAuthorize("hasRole('ORGANIZER')")`; `EndpointAuthorizationGuardTest` green; 401/403/404/400 documented; request/response examples in the spec.
2. **AC2 (adapters)** — Audience logic sits behind one interface in EMS `ch.batbern.events.mailcenter.compose`:
   `AudienceAdapter { audience(); allowedCategories(); allowsTemplate(t); allowedVariables(); credentialVariables(); resolveRecipients(ctx); variables(ctx, recipient, locale); deliver(ctx, trigger) }`.
   Four implementations: `NewsletterAudienceAdapter`, `RegistrantAudienceAdapter`, `SpeakerAudienceAdapter`, `VenueAudienceAdapter`. `MailComposerService` holds no audience-specific `if`/`switch`.
3. **AC3 (one render path, byte-equal)** — GIVEN an audience, a template and recipient *i*, WHEN the organizer previews recipient *i* and then sends, THEN the preview's `html` and `subject` are byte-equal to the `body_html` and `subject` recorded for recipient *i* (`mail_recipient.body_html` when present, otherwise `mail_message.body_html`). One integration test per audience. Both sides are after `MailRedaction` (16.1a Dev Notes), so the preview never shows a token.
4. **AC4 (real recipients, stepping)** — `POST .../preview` with `recipientIndex = i` renders for the *i*-th recipient of the same ordered list the send uses. The response carries `recipientIndex`, `recipientCount`, `recipient {displayName, address}`, up to 8 `sampleRecipients`, `personalized` (false for newsletter and venue: one rendering for everyone), the resolved `templateKey`, `replyTo`, `organizerCount`, `skipped[]` (recipients excluded and why) and `unknownPlaceholders[]`.
5. **AC5 (test send)** — GIVEN `target=ORGANIZERS` and `recipientIndex = i`, WHEN sent, THEN:
   - the mail rendered for recipient *i* goes to **every** ORGANIZER-role user (CUMS `getOrganizerUsernames`), subscribed or not, one mail each;
   - the subject starts with `[TEST] `;
   - every credential variable of the adapter (`unsubscribeLink`, `deregistrationUrl`, ...) is replaced by a dummy value before rendering, so a test mail never carries a working link of a real recipient;
   - no real recipient receives anything, no `newsletter_sends` row is created, the registrant one-shot guard is not consumed;
   - one `mail_message` is recorded with `is_test = true`, the composed audience, the organizer as sender.
6. **AC6 (send to recipients)** — `target=RECIPIENTS` delivers through the adapter, reusing the existing send code:
   - Newsletter: `NewsletterEmailService.sendNewsletter` (async job, `newsletter_sends`, in-progress 409, ADR-015 test-event wall) unchanged;
   - Registrants: `SlidesOnlineEmailService.sendRegistrantNotice` with its one-shot guard (409 when already sent) unchanged;
   - Speakers: the per-speaker loop moves server-side into one request, one `MailTrigger`, so a send to 8 speakers is one recorded mail per template key, not 8;
   - Venue: one mail, first role To, the rest Cc, Reply-To the coordinator, unchanged.
   The response returns the `mailKeys` of the recorded mail(s) at once (the row is opened before delivery, see Dev Notes), `recipientCount` and `skipped[]`.
7. **AC7 (speakers: template, tier)** — The speaker template list is exactly the six `speaker-reminder-{response,content}-tier{1,2,3}` templates. `templateKey = null` means "automatic": each speaker gets the tier `SpeakerReminderService.autoDetectTier` picks for *their* deadline; the preview shows which template recipient *i* gets. A response template targets INVITED speakers, a content template ACCEPTED speakers; "automatic" needs `speakerReminderType` (`RESPONSE | CONTENT`). Choosing a template overrides the tier for every recipient. Reminder log, outreach history and the TIER_3 escalation still happen per speaker, as today. Speakers with reminders disabled are in `skipped[]`, not in the count.
8. **AC8 (progress and retry)** — GIVEN a newsletter or registrant-notice send, WHEN the composer polls `GET /mails/{mailKey}`, THEN `progress {status, total, sent, failed, retryable}` comes from the linked `newsletter_sends` row. `POST /mails/{mailKey}/retry` retries a PARTIAL/FAILED newsletter send (409 while one is running, 400 for a mail that is not a newsletter). For other audiences `progress` is null.
9. **AC9 (tests hidden by default)** — `GET /mails` and `/counts` exclude `is_test` rows unless `includeTests=true`. This also hides the old newsletter test-mode sends (they are recorded with `is_test = true`); intended.
10. **AC10 (validation)** — The backend is the authority: a template the adapter does not allow, an unknown audience, a `recipientIndex` out of range, `venueRecipients` empty, or `speakerReminderType` missing in automatic mode → 400 with a message. An unknown event → 404.

### 16.8b — Composer UI

11. **AC11 (same steps everywhere)** — "+ New mail" → Newsletter, Registrants, Speakers, Venue & Catering open one `MailComposer` with the same steps and layout (spec "Composer" 1–8): template (the [Template | Own text] toggle is rendered but Own text is disabled with a "coming soon" hint until 16.5), language, audience-specific fields, recipients (count + up to 8 chips, from the server), preview with ‹ › and "(i/n)" for personalised audiences, reply note, footer with **[Send to organizers (test)]** (outlined) and **[Send to N recipients]** (primary) followed by one inline confirm ("Really send to …?" · Back · Yes, send).
12. **AC12 (layout)** — Desktop: inline at the top of the page, as `EventCommunicationsContainer` does today (no Drawer). Mobile (`useBreakpoints().isMobile`): the existing full-screen dialog; footer buttons stay visible (sticky); no horizontal scroll at 375 px.
13. **AC13 (after sending)** — Real send: the composer closes, a toast confirms, the row for the returned `mailKey` is highlighted at the top of "What went out", showing "sending …" while `progress.status` is PENDING/IN_PROGRESS, then the counts. Test send: the composer stays open, the toast says "Test sent to N organizers". A newsletter that ends PARTIAL/FAILED shows Retry on its row (AC8).
14. **AC14 (errors)** — 409 (newsletter running, registrant notice already sent) shows the reason inline, not a generic error. A send with `skipped[]` lists who was skipped and why.
15. **AC15 (Partners unchanged)** — The Partners entry still opens `PartnerMeetingsCompose` (16.9 adds the plain partner path).
16. **AC16 (i18n, a11y)** — New keys in all 10 locales (EN + DE first-class). Keyboard: focus moves to the composer heading on open, Esc closes, focus returns to "+ New mail"; ‹ › are buttons with labels; the preview iframe has a title.

### All parts

17. **AC17 (mail safety)** — No Bruno, Playwright or integration test sends real mail. Bruno gets only non-sending requests (preview, 400, 403). Playwright send specs are tagged `@sends-real-email` and skip outside `development`. Backend send tests mock or capture `EmailService` (no SES client).

### 16.8c — Clean-up (after one release)

18. **AC18** — Removed: `SpeakerBulkComms.tsx`, `EventRegistrantNoticesTab.tsx`, the compose body of `VenueCoordinationComposer.tsx` / `EventVenueTab.tsx`, the compose section of `EventNewsletterTab.tsx`, their tests and i18n keys no longer used; the operations listed in Dev Notes → "Endpoints retired in 16.8c" with their controller methods, service-only-used code, spec entries, generated types and Bruno files. `GET /events/{code}/newsletter/history` is among them. Full EMS suite, Vitest, type-check, `check:api-types` green.

## Tasks / Subtasks

### Part 16.8a — backend (PR 1)

- [x] **Task 1 — Contract first** (AC: 1, 4, 5, 6, 8, 9, 10)
  - [x] Extend `docs/api/event-mail-api.openapi.yml`: schemas `MailComposeRequest`, `MailPreviewResponse`, `MailSendRequest` (= compose request + `target`), `MailSendResponse`, `MailProgress`, `MailComposeRecipient`, `MailSkippedRecipient`; operations `previewEventMail`, `sendEventMail`, `retryEventMail`; `progress` on `MailMessageDetail`; `includeTests` on list and counts. Field list in Dev Notes.
  - [x] Regenerate frontend types (`npm run generate:api-types:event-mail`) and commit them; the EMS generator already reads this spec (`services/event-management-service/build.gradle:856`).
- [x] **Task 2 — Shared kernel** (AC: 5)
  - [x] `MailContext.asTest()` (same context, `test = true`). Unit test in `MailContextTest`.
  - [x] Nothing else in shared kernel; `MailContext.of(...)` stays the factory.
- [x] **Task 3 — RED: tests first** (AC: 2–10, 17)
  - [x] `MailComposerControllerIntegrationTest` (extends `AbstractIntegrationTest`, `@Transactional`): 403 for non-organizer on all three new operations, 400 cases of AC10, 404 unknown event.
  - [x] `MailComposerByteEqualIntegrationTest`: per audience, preview recipient *i* then send; capture what `EmailService` sends and what `JpaOutboundMailRecorder` stored; assert byte-equal subject + body (AC3). Use a capturing `EmailService` stub or the local-capture branch (`EmailService` with no SES client records `local-{id}` Message-IDs, 16.1a); never an SES client.
  - [x] `MailComposerTestSendIntegrationTest`: organizer list from a stubbed `UserApiClient`; asserts AC5 (all organizers, `[TEST] ` prefix, credential dummies, no `newsletter_sends`, `is_test = true`, registrant guard not consumed).
  - [x] `SpeakerAudienceAdapterTest` (unit): template list = six keys; automatic tier per speaker; reminder type ↔ status; disabled speakers skipped (AC7).
  - [x] `MailMessageQueryService` tests: `includeTests` default false (AC9); `progress` from `newsletter_sends` (AC8).
- [x] **Task 4 — One render path** (AC: 3)
  - [x] `MailRenderer` in `mailcenter.compose`: `render(TemplateSource src, String locale, Map<String,String> vars) → RenderedMail(subject, html)`, doing exactly what the four services do today: `replaceVariables(content)` → `mergeWithLayout(..., "batbern-default", locale)` → `replaceVariables(merged)`; subject = `replaceVariables(subject)`. `TemplateSource` = DB template (key, locale, DE fallback as `VenueCoordinationService.loadTemplateOrThrow` does) or classpath fallback (speaker reminders, `SpeakerReminderEmailService.loadHtmlContent`).
  - [x] Refactor the send loops to call the **same** adapter render function the preview calls, not a copy: `NewsletterEmailService` (`renderContent` + `mergeWithLayout` at :226-231 and inside the loops at :403-416, :455-480, retry :588-623), `SlidesOnlineEmailService.renderMail` + per-recipient layering (:299-311, :383-403), `SpeakerReminderEmailService.loadReminderTemplate` (:162-203), `VenueCoordinationService.render` (:163-170). Keep each service's existing quirks (newsletter `buildSubject` fallback, speaker subject fallback, registrant per-recipient locale) inside the adapter/variables, not inside `MailRenderer`.
  - [x] `PlaceholderScanner`: returns every `{{name}}`, `{{#name}}`, `{{/name}}` left in a rendered subject or html. Preview reports them in `unknownPlaceholders` (warning only for templates in this story; 16.5 makes it blocking for own text).
- [x] **Task 5 — Adapters** (AC: 2, 4, 6, 7)
  - [x] `AudienceAdapter` interface + `ComposeContext` record (event, request, organizer username) + `ComposeRecipient` record (displayName, address, cc, locale, per-recipient key such as speakerPoolId or registration id, internal only).
  - [x] `NewsletterAudienceAdapter`: categories `NEWSLETTER`; recipients = active subscribers in keyset order (`findActiveAfterId`, as the send job); `personalized = false` (only `unsubscribeLink` differs, and it is redacted); extra field `newsletterReminder`; credential variables `unsubscribeLink`; `deliver` → `sendNewsletter(...)` with `testMode = false`.
  - [x] `RegistrantAudienceAdapter`: categories `REGISTRANT_NOTICE`; recipients = the exact filter of `processSend` (active statuses, resolvable email, case-insensitive dedupe, newsletter opt-out skipped → `skipped[]` with reason `OPTED_OUT`); per-recipient locale (`resolveLocale`), so the preview's `locale` only applies when the organizer overrides it for viewing (see Dev Notes); credential variables `deregistrationUrl`; secrets: the registration code (`withSecrets`, as today :308).
  - [x] `SpeakerAudienceAdapter`: allowed templates = the six reminder keys; recipients = speaker pool entries in the target status, ordered by speaker name, `remindersDisabled` → `skipped[]` with `REMINDERS_DISABLED`, no resolvable primary email → `NO_EMAIL`; locale per speaker (`speakerLocale`, `SpeakerReminderService` :334-341); variables as `SpeakerReminderEmailService` :191-202; Reply-To `ModeratorReplyAddress.forEvent`. `deliver` → new `SpeakerReminderService.sendManualReminders(event, reminderType, templateKeyOrAuto, trigger, organizer)` that loops `sendManualReminder`'s body with **one** `MailTrigger.organizer(...)` passed through (today each call creates its own trigger, :190).
  - [x] `VenueAudienceAdapter`: categories `VENUE_COORDINATION`; extra fields `venueRecipients` (`VENUE`, `CATERING`) and `venueNotes`; one recipient entry (To + Cc); `personalized = false`; Reply-To coordinator; `deliver` → `VenueCoordinationService.send(...)`.
- [x] **Task 6 — `MailComposerService` + controller** (AC: 1, 4, 5, 6, 10)
  - [x] `preview(eventCode, request)`: adapter lookup by audience, template check, recipient *i*, render, redact, scan, build response.
  - [x] `send(eventCode, request, organizer)`: `ORGANIZERS` → test path (render recipient *i* with credential dummies, `[TEST] ` prefix, loop organizers via `emailService.sendHtmlEmailSync(context.asTest(), ...)`; newsletter ICS attachments included when the template carries them); `RECIPIENTS` → `adapter.deliver(...)`.
  - [x] Open the record before delivery so the response can return `mailKeys` (Dev Notes → "mailKey before the async send").
  - [x] Controller methods on `EventMailController` (same generated `EventMailApi`), `@PreAuthorize("hasRole('ORGANIZER')")`, `Cache-Control: no-store` on preview (bodies are PII).
- [x] **Task 7 — Progress, retry, test filter** (AC: 8, 9)
  - [x] `MailMessageQueryService`: `includeTests` in `ENTRIES` (`AND (:includeTests OR NOT is_test)`) for list and counts; `progress` for rows with `newsletter_send_id` (status, recipient_count, sent_count, failed_count, retryable = status in PARTIAL/FAILED and no IN_PROGRESS send for the event).
  - [x] `retryEventMail`: resolve `newsletter_send_id` from the mail key (also the `newsletter-{sendId}` backfill keys) → `NewsletterEmailService.retryFailedRecipients`.
- [x] **Task 8 — Bruno (read-only, local dev)** (AC: 1, 10, 17)
  - [x] `bruno-tests/events/mails-preview-*.bru` (newsletter preview 200, 400 unknown template, 403 partner/speaker token where available). **No `POST /mails` file**: the Bruno suite runs against production after each deploy. Prose in `docs { }`, never `#`.
- [x] **Task 9 — GREEN + verify** (AC: all of 16.8a)
  - [x] Full EMS suite, output to a file: `set -o pipefail; ./gradlew :services:event-management-service:test 2>&1 | tee /tmp/ems-16-8a.log`.
  - [x] Local dev (`make dev-native-up`): preview each audience, step speakers; send one test to organizers and one real send per audience on a **local throwaway event**; compare `/dev/emails` capture with the recorded `mail_message` (subject, body). Note in the Dev Agent Record what was and was not checked.
  - [x] Docs: `docs/architecture/06-backend-architecture.md` (mailcenter section: compose API, adapters), `docs/user-guide/features/event-mail.md` (consult `.github/doc-drift-mappings.yml`).

### Part 16.8b — frontend (PR 2)

- [x] **Task 10 — Service + hooks** (AC: 11, 13)
  - [x] `mailMessageService.ts`: `previewMail`, `sendMail`, `retryMail`; types from `@/types/generated/event-mail-api.types`.
  - [x] `useMailMessages.ts`: `useMailPreview` (query keyed on the request incl. `recipientIndex`, `keepPreviousData` while stepping), `useSendMail`, `useRetryMail`, `useMailMessage(mailKey, {poll})` polling every 3 s while `progress.status` is PENDING/IN_PROGRESS (as `useSendStatus` does today).
- [x] **Task 11 — RED: Vitest first** (AC: 11–16)
  - [x] `MailComposer.test.tsx` (MSW, not service mocks): steps render for each audience; ‹ › changes `recipientIndex`; "(3/8)"; unknown placeholders shown; test button → toast, stays open; send → confirm → `onSent(mailKeys)`; 409 message; `skipped[]` list; Own text toggle disabled.
  - [x] Adapter tests: speaker template list shows six + "Automatic"; choosing a content template switches the recipient hint; venue role checkboxes; newsletter reminder switch.
  - [x] `EventCommunicationsContainer.test.tsx`: picker opens `MailComposer` for four audiences, `PartnerMeetingsCompose` for partners; focus handling unchanged.
  - [x] `MailSentList.test.tsx`: highlighted row, "sending …", Retry on PARTIAL, "show test sends" toggle (AC9).
- [x] **Task 12 — Composer** (AC: 11, 12, 14, 16)
  - [x] `web-frontend/src/components/organizer/EventPage/communications/composer/MailComposer.tsx` (steps), `ComposerPreview.tsx` (sandboxed iframe, no `allow-scripts`, as `MailDetail.tsx`), `ComposerRecipients.tsx`, `ComposerFooter.tsx` (sticky on mobile, inline confirm).
  - [x] `composer/audiences/{newsletter,registrants,speakers,venue}.tsx`: each exports a `ComposerAudience` object `{ audience, templateCategory, templateFilter?, defaultTemplateKey?, personalized, ExtraFields, toRequest(state) }`. Templates from the existing `useEmailTemplates({ category })`; the backend re-checks (AC10).
  - [x] Venue extra fields reuse the role checkboxes and notes from `VenueCoordinationComposer.tsx` (extract, do not copy).
- [x] **Task 13 — Wire into the page** (AC: 12, 13, 15)
  - [x] `EventCommunicationsContainer.tsx`: the `composer` block (:114-134) mounts `MailComposer` for the four audiences; `handleSent(mailKeys)` invalidates, closes, highlights.
  - [x] `MailSentList.tsx`: `highlightKey` prop, progress line, Retry button, "show test sends" switch (`includeTests`).
- [x] **Task 14 — i18n** (AC: 16): `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` under `eventPage.mail.composer.*`.
- [x] **Task 15 — Playwright (local dev only)** (AC: 11, 12, 17)
  - [x] `e2e/organizer/event-mail-composer.spec.ts`: opens each audience, steps the preview, checks the 375 px layout. **Does not click a send button.**
  - [x] `e2e/organizer/event-mail-composer-send.spec.ts`: one test send + one real send on a throwaway event, tagged `@sends-real-email`, guarded by `EMAIL_SAFE_ENV` exactly like `event-newsletter-send.spec.ts:41-43`. Replace the send part of `event-newsletter-send.spec.ts` or point it at the composer.
- [x] **Task 16 — Verify**: lint, type-check, `check:api-types`, full Vitest to a file; Playwright on local dev; screenshot-free (PII).

### Part 16.8c — clean-up (PR 3, after one release, owner go required)

- [ ] **Task 17 — Remove** (AC: 18): components and endpoints listed in Dev Notes; spec entries in `event-newsletter-api.openapi.yml`, `events-core-api.openapi.yml`, `event-speakers-api.openapi.yml`; regenerate types; Bruno `bruno-tests/events/venue-coordination-preview.bru` and `bruno-tests/speaker-pool-api/61..66-send-reminder-*.bru` (move the negative cases worth keeping to `mails-preview-*`); `useNewsletterHistory`, `useSendStatus`, `useRetryFailedRecipients`, `useSendReminder`, `usePreviewRegistrantNotice`, `useSendRegistrantNotice` and their services once unused.
- [ ] **Task 18 — Verify**: grep the repo for each removed operationId and path; full EMS + Vitest + Playwright; docs updated (`[no-doc]` not allowed here, the user guide changes).

## Dev Notes

### What exists today (verified in code at 15a13959)

| Audience | Frontend | Endpoints | Service | Recorded as (16.1a) |
|---|---|---|---|---|
| Newsletter | `EventNewsletterTab.tsx` (template select by locale :116-127, test-mode switch :327-333, preview :348-406, retry :413-430) | `POST /events/{c}/newsletter/preview`, `/send`, `GET /history`, `GET /sends/{id}/status`, `POST /sends/{id}/retry` (`NewsletterController.java:195-275`) | `NewsletterEmailService` (async job, keyset paging, ICS attachments) | one row linked to `newsletter_sends` (`mailContext(sendId, ...)` :372) |
| Registrants | `EventRegistrantNoticesTab.tsx` (409 handling :137-152, polls newsletter status :71) | `POST /events/{c}/registrant-notices/preview`, `/send` (`RegistrantNoticeController`) | `SlidesOnlineEmailService` (async, `newsletter_sends`, one-shot guard :162-176) | one row linked to `newsletter_sends` (:266-268) |
| Speakers | `SpeakerBulkComms.tsx` (status filter :58-60, client loop :90-117) | `POST /events/{c}/speaker-pool/{id}/send-reminder` (`SpeakerReminderController.java:48`) | `SpeakerReminderService.sendManualReminder` :153-197 → `SpeakerReminderEmailService` | **one row per speaker** (new `MailTrigger.organizer` per call, :190) |
| Venue & Catering | `EventVenueTab.tsx` → `VenueCoordinationComposer.tsx` | `POST /events/{c}/venue-coordination/preview`, `/send` (`events-core-api.openapi.yml:1577-1650`) | `VenueCoordinationService` :94-161 | one row, To + Cc |
| Partners | `PartnerMeetingsCompose.tsx` → `PartnerMeetingInviteAction.tsx` | partner-coordination `POST /partner-meetings/{id}/send-invite` | `PartnerInviteEmailService` | **not recorded** (16.9) |

**The epic's drift table, checked:**
- Newsletter test send goes only to organizers who are also subscribers: **confirmed** (`NewsletterEmailService.getTestModeRecipientCount` :241-246, job :394-423). Its subject prefix is `[Testmailing nur an OK] ` (:377), not `[TEST]`; the composer uses `[TEST] ` for every audience.
- Registrant preview uses sample data: **confirmed** (`SlidesOnlineEmailService` :207-213, "Erika Muster"). Add: registrants get the mail **in their own language** (`resolveLocale` :299, :452-468); the preview language is only what the organizer views.
- Speakers have no preview and the frontend loops: **confirmed**. Add: the endpoint already accepts an optional `tier` (`SpeakerReminderController.java:79`), the UI never sends it. The tier is auto-detected **per speaker** from that speaker's deadline (`autoDetectTier` :218-233), so one composer send can mix tiers. Speakers also get mail in their own language (#1119, :334-341).
- Venue preview "server-rendered per recipient": **not quite**. It is one mail for all selected roles, with a combined salutation (`buildVariables` :198), first role To, rest Cc (:137-143). Treat venue as one recipient entry with Cc, `personalized = false`.
- Partners: one To: line, hard-coded Java body: **confirmed** (`PartnerInviteEmailService.java:88-117`). Cancellation is not a separate action; it is sent when a meeting with a sent invite is deleted (`PartnerMeetingService.java:189-210`).
- Old endpoints: the epic speaks of "five"; there are **seven** compose operations in four specs (newsletter preview + send, registrant-notices preview + send, venue preview + send, speaker send-reminder), plus `newsletter/history`, `newsletter/sends/{id}/status` and `/retry`, plus an unused `POST /events/{c}/slides-online/send` (no frontend or Bruno caller found).

### Variables per audience (today, verified)

- Newsletter (`NewsletterEmailService.buildVariables` :701-723): `reminderPrefix, eventNumber, eventType, eventTitle, eventDate, eventTime, venue, venueDirectionsUrl, conferenceLanguage, speakersSection, currentYear, eventDetailLink, registrationLink, upcomingEventsSection, unsubscribeLink, preferencesLink, logoUrl`.
- Registrants (`SlidesOnlineEmailService` :405-445): `eventTitle, eventNumber, eventType, eventDate, eventDetailLink, currentYear, logoUrl, eventUrl, dashboardLink, supportUrl` + per recipient `deregistrationUrl, recipientName, attendeeFirstName`.
- Speakers (`SpeakerReminderEmailService` :191-202): `speakerName, eventTitle, eventDate, deadline, daysRemaining, portalLink, organizerName, organizerEmail, currentYear, logoUrl`.
- Venue (`VenueCoordinationService` :191-212): `eventDate, eventTitle, topicTitle, venueName, salutation, agenda, notes, organizerSignature, currentYear, logoUrl`.

`allowedVariables()` returns these sets in 16.8a (the preview's placeholder scan needs them); 16.5 curates which ones become chips.

### Template rules

- Store: `email_templates` (V62), category is a free `VARCHAR(50)`, no DB check; the category list is an enum in `event-newsletter-api.openapi.yml` (:51-61, :1152-1163, :1208-1219) and in `web-frontend/src/components/organizer/Admin/EmailTemplatesTab.tsx:64-94`. Seeding derives the category from the key prefix (`EmailTemplateSeedService.deriveCategory` :151-201).
- **The SPEAKER category is not a safe allow-list for the speaker audience**: it also holds `speaker-invitation`, `speaker-acceptance` and `qna-new-questions`. `SpeakerAudienceAdapter.allowsTemplate` checks the key: `speaker-reminder-(response|content)-tier[123]`.
- Newsletter must keep rejecting REGISTRANT_NOTICE templates and the registrant path must keep requiring them (`rejectRegistrantTemplate`, `requireRegistrantNoticeTemplate` :232-245). The adapters' `allowsTemplate` replaces neither; they stay as defence in depth.
- Key derivation: `SpeakerReminderEmailService.templateKey` :156-159 (`RESPONSE + TIER_1 → speaker-reminder-response-tier1`). Reverse it in the adapter (key → reminder type + tier) for an explicit choice.

### `mailKey` before the async send

`JpaOutboundMailRecorder` creates the `mail_message` row on the group's **first recorded send** (`INSERT_IF_ABSENT` :42-50). The newsletter and registrant sends are async and speaker reminders use the async `sendHtmlEmail` (`SpeakerReminderEmailService` :113), so the row may not exist when `POST /mails` returns. Add `JpaOutboundMailRecorder.open(MailContext, subject, bodyHtml) → mailKey` that runs the same `INSERT_IF_ABSENT` and returns the key; the recorder's later inserts become no-ops (same `send_group_id`). Adapters compute their `MailContext`(s) before delivering:
- newsletter / registrants: `MailTrigger.forSend(sendId, ...)` → the adapter needs the `sendId`; split `sendNewsletter` / `sendRegistrantNotice` so the audit row is created first and its id is available before the async job starts (they already create it first, :323-331 and :180-183; return it).
- speakers: one trigger for the request; one context per template key actually used (automatic tier may give two or three).
- venue: one context.
Open each row with recipient 0's rendered, redacted subject and body. A send where every recipient then fails before SES leaves an empty row (`recipient_count = 0`); acceptable, it shows as "0 recipients".

### Test send details

- Organizer addresses: `UserApiClient.getOrganizerUsernames()` (:120) then `getUserByUsername(...).getEmail()`; dedupe case-insensitively.
- Credential dummies: newsletter `unsubscribeLink = {baseUrl}/unsubscribe?token=TEST`; registrants `deregistrationUrl = {baseUrl}/deregister?token=TEST`. `MailRedaction` masks `token=` anyway when recording, but the dummy protects the organizer's inbox copy: a test must never carry a real recipient's working link.
- Context: `MailContext.of(MailTrigger.organizer(user), eventCode, audience, templateKey).asTest()`. One group for the whole test send.
- ADR-015: `EmailService` still runs `assertNotTestMarked` on the live branch. Do not bypass it.
- The old newsletter `testMode` stays in `NewsletterEmailService` until 16.8c; the composer never sets it.

### Request and response fields (spec)

`MailComposeRequest`: `audience` (`NEWSLETTER | REGISTRANTS | SPEAKERS | VENUE`; `PARTNERS` added by 16.9), `contentSource` (`TEMPLATE`; `OWN_TEXT` reserved, 400 until 16.5), `templateKey` (nullable; null = automatic for speakers), `locale` (`de | en`), `recipientIndex` (preview, default 0), `newsletterReminder` (bool), `speakerReminderType` (`RESPONSE | CONTENT`, automatic mode only), `venueRecipients` (`[VENUE, CATERING]`), `venueNotes` (string, max 4000). Flat optional fields, not `oneOf` (the Java generator handles `oneOf` badly); each documented with the audience it applies to.
`MailSendRequest` = `MailComposeRequest` + `target` (`RECIPIENTS | ORGANIZERS`).
`MailPreviewResponse`: `subject`, `html`, `recipientIndex`, `recipientCount`, `recipient` (`displayName`, `address`, `cc[]`), `sampleRecipients[]` (≤ 8, `displayName` only), `personalized`, `templateKey`, `locale`, `replyTo`, `organizerCount`, `skipped[]` (`displayName`, `reason`: `OPTED_OUT | REMINDERS_DISABLED | NO_EMAIL | DUPLICATE`), `unknownPlaceholders[]`.
`MailSendResponse`: `mailKeys[]`, `target`, `recipientCount`, `skipped[]`.
`MailProgress`: `status` (`PENDING | IN_PROGRESS | COMPLETED | PARTIAL | FAILED`), `total`, `sent`, `failed`, `retryable`.

### Endpoints retired in 16.8c

`POST /events/{c}/newsletter/preview`, `POST /events/{c}/newsletter/send`, `GET /events/{c}/newsletter/history`, `GET /events/{c}/newsletter/sends/{id}/status`, `POST /events/{c}/newsletter/sends/{id}/retry`, `POST /events/{c}/registrant-notices/preview`, `POST /events/{c}/registrant-notices/send`, `POST /events/{c}/venue-coordination/preview`, `POST /events/{c}/venue-coordination/send`, `POST /events/{c}/speaker-pool/{id}/send-reminder`, and the unused `POST /events/{c}/slides-online/send`. Status and retry are not in the owner's list; they move because the composer no longer needs them (AC8). Confirm with the owner before 16.8c; if he wants them kept, drop them from this list.
Keep: the subscriber endpoints, `/email-templates`, `PUT .../reminders-disabled`, the scheduler paths (automatic reminders, registrant notices triggered by the system).

### Rules that bite

- **ADR-008:** every new operation `@PreAuthorize("hasRole('ORGANIZER')")` on the controller method.
- **ADR-003:** no UUID in the API. `recipientIndex` instead of a speaker or registration id; `mailKey` instead of `newsletter_sends.id` (the backfill key `newsletter-{uuid}` exists since 16.3; do not add new UUID keys).
- **Enums:** UPPER_CASE in JSON and Java; no `@JsonValue`.
- **`GlobalExceptionHandler`:** 400 for validation, 409 for `DuplicateNewsletterSendException` / `SlidesOnlineAlreadySentException` (check they already map to 409; they do for the old endpoints).
- **No migration needed for 16.8.** `mail_message.is_test` exists (V135 line 26). EMS head is V136; if a migration turns out to be needed, re-check the head first: the parallel inbound stories (16.2) also add migrations.
- **Mail safety:** AWS is production. Never point a send test at staging. Local dev captures to `/dev/emails`. Throwaway events only; delete local test rows afterwards.
- **Checkstyle:** braces everywhere, `+` at line start, no unused imports.
- **PR = production deploy.** Push, then ask the owner before opening each PR.

### References

- `docs/prd/epic-16-mail-center.md` → Story 16.8
- `docs/ux/mail-center-spec.md`, `docs/ux/mail-center-layouts.html`, `docs/ux/mail-center-mobile.html`
- `docs/architecture/ADR-015-test-data-markers-and-email-send-guards.md`, `ADR-008-simplified-api-gateway-routing.md`, `ADR-013-rest-api-crud-conventions.md`
- `docs/api/event-mail-api.openapi.yml`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/{MailContext,MailTrigger,OutboundMail,OutboundMailRecorder,MailRedaction}.java`
- `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java` (`replaceVariables` :657-692, `record` :602-614)
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/{EventMailController,MailMessageQueryService,JpaOutboundMailRecorder}.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/{NewsletterEmailService,SlidesOnlineEmailService,SpeakerReminderService,SpeakerReminderEmailService,VenueCoordinationService,EmailTemplateSeedService}.java`
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`, `communications/MailSentList.tsx`, `communications/MailDetail.tsx`
- `_bmad-output/implementation-artifacts/16-1a-record-outbound-mail.md` (redaction, send groups), `16-4-new-mail-picker.md` (inline layout change 2026-10-31)

## Dev Agent Record

### Agent Model Used

Claude Opus 5.5 (Amelia, dev-story), 2026-11-01. Part 16.8a on `feature/16-8a-composer-api`; Part 16.8b on
`feature/16-8b-composer-ui`, branched from the 16.8a branch (16.8a not yet on develop, so its PR must go first).

### Debug Log References

- EMS full suite with checkstyle: 2236 tests, 0 failures, 0 errors, 18 skipped (pre-existing).
  Shared kernel suite green. Frontend `type-check` clean; regenerated `event-mail-api.types.ts`.
- RED seen first: the new tests did not compile, then failed for the expected reasons
  (`Map.copyOf` with null variable values, per-recipient subject, 500 from a mocked EmailService).
- Local dev (live, through the gateway at :8000, throwaway event `BATbern990` with two synthetic
  registrants, removed afterwards):
  - previews 200 for all four audiences (newsletter 1010 subscribers, registrants 2, venue 1,
    speakers on BATbern60 CONTENT tier 2); own text 400; no real token in any preview body.
  - test sends 202 for newsletter, registrants, speakers: recorded `is_test`, subject `[TEST] `;
    captured copies carry only `token=TEST`, the newsletter copy its `.ics`.
  - real sends 202: registrants (recorded, one-shot guard then 409), venue (with simulator contacts
    set temporarily in the local config, restored after): preview == record == redacted capture.
  - Bruno `events/mails-preview-*` + existing `mails-*`: 6 files, 12/12 tests, 403 for the partner
    token, no "Skipping invalid file".
- Not checked: the newsletter real send on local dev (1010 local subscribers would flush the
  200-mail capture buffer); it is covered by `MailComposerSendIntegrationTest`. Nothing ran against
  staging/production.

- **16.8b:**
  - Vitest full suite: 420 files, 5535 passed, 0 failed. Lint (max-warnings 0), `tsc --noEmit`,
    Prettier, `check:api-types` clean. RED seen first (missing module, then 4 MailSentList and 6
    container tests); two deliberate mutations of `MailComposer` were caught by the tests.
  - Local dev, live through the gateway: preview of every audience and every template of
    REGISTRANT_NOTICE and VENUE_COORDINATION on BATbern60 reports no unknown placeholder
    (newsletter 1010 recipients, registrants 61 + 1 skipped, speakers CONTENT 1, venue 1).
  - Playwright on local dev: `event-mail-composer.spec.ts` 6/6 and `event-mail-page.spec.ts` 2/2;
    `event-mail-composer-send.spec.ts` (`@sends-real-email`) 2/2. Captured mail checked directly:
    test send = 2 organizer copies, subject `[TEST] `, only `token=TEST`; real send carries the
    subscribers' real unsubscribe tokens. The 2 `mail_message` rows of the deleted fixture event
    were removed by hand (retention keeps mail of a deleted event 12 months by design, 16.1a AC6).
  - Nothing ran against staging/production.

### Completion Notes List

- Contract: preview, send (`POST /events/{code}/mails`), retry, `progress` on the detail,
  `includeTests` on list and counts, all on the one mail resource.
- `MailComposerService` + `AudienceAdapter` with four adapters; the service holds no audience
  branch. Each adapter renders through the same service method its send uses: newsletter
  `prepare`/`renderBody` (the job, its retry and test mode now use them too), registrants
  `resolveRecipients`/`renderBase`/`personalise` (the send loop uses them), speakers
  `SpeakerReminderEmailService.render` (the send uses it), venue `renderFor`.
- `MailRenderer` is static (content → layout → variables) so existing service tests that mock
  `EmailService` keep working unchanged.
- Speakers: one request, one `MailTrigger`, `SpeakerReminderService.sendManualReminders`; one record
  per template key. Reminder log, outreach history, escalation per speaker as before.
- Test send: every organizer address from CUMS, deduplicated; an organizer whose copy fails is
  logged by exception type only (the message can contain the address) and skipped.
  `organizerCount` counts reachable addresses.
- Deviations from the task text: the byte-equal and test-send integration tests share one class
  (`MailComposerSendIntegrationTest`, same fixtures); the query tests for `includeTests` and
  `progress` are in `EventMailControllerIntegrationTest`. Integration tests must replace
  `TestAwsConfig`'s bare `EmailService` mock with `@MockitoBean(answers = CALLS_REAL_METHODS)`,
  otherwise rendering returns null and an equality test passes vacuously.
- Known limit: `mail_recipient` has no subject, so for a registrant whose language differs from the
  first recipient's the record holds the first recipient's subject. Body is per recipient.
- Visible change: test sends (including old newsletter test-mode sends) are hidden from "What went
  out" until 16.8b adds the "show test sends" switch.
- **16.8b:** `MailComposer` + `ComposerRecipients` / `ComposerPreview` / `ComposerFooter`; one
  `ComposerAudience` per audience (`audiences/*.ts`, field components in their own `.tsx` for fast
  refresh). The composer has no audience branch. Preview is a query keyed on the request
  (`keepPreviousData` while stepping); venue notes are debounced 400 ms. The preselected template is
  derived, not stored, so the first request is already the right one.
- Venue roles and notes extracted to `VenueRecipientFields` + `useVenueCoordinationConfig`; the old
  `VenueCoordinationComposer` uses them until 16.8c deletes it (its 9 tests pass unchanged).
- Found and fixed on local dev: ‹ › and the test button were disabled while their request ran, so the
  focused button dropped focus to `<body>` and Esc no longer closed the composer. They now stay
  enabled and ignore clicks while busy (`aria-busy`); two Vitest regressions, one shown to fail on the
  old code.
- Deviations from the task text: the audience tests are in `MailComposer.test.tsx` (they drive the
  real adapters through the composer, with MSW); `MailSentList` takes `highlightKeys: string[]`
  (a speaker send can return up to three keys); Retry sits beside the row (a button inside the row
  button is not operable). `event-newsletter-send.spec.ts` is deleted, its scenario lives in
  `event-mail-composer-send.spec.ts`.
- The old compose components (`EventNewsletterTab`, `EventRegistrantNoticesTab`, `SpeakerBulkComms`,
  `EventVenueTab`) are no longer mounted but stay in the tree with their tests until 16.8c.
- MSW `setupServer` is used for the first time in a component test here; it intercepts the axios
  client under jsdom without extra setup.

### File List

- `docs/api/event-mail-api.openapi.yml`
- `web-frontend/src/types/generated/event-mail-api.types.ts`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/MailContext.java`
- `shared-kernel/src/test/java/ch/batbern/shared/mail/MailContextTest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/EventMailController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/JpaOutboundMailRecorder.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailMessageQueryService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/compose/` (new):
  `AudienceAdapter`, `ComposeContext`, `ComposeRecipient`, `ComposeRequest`, `DeliveredMail`,
  `MailComposerService`, `MailRenderer`, `NewsletterAudienceAdapter`, `PlaceholderScanner`,
  `Recipients`, `RegistrantAudienceAdapter`, `Rendering`, `SkippedRecipient`,
  `SpeakerAudienceAdapter`, `TemplateCategories`, `VenueAudienceAdapter`
- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SlidesOnlineEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerReminderEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerReminderService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/VenueCoordinationService.java`
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/EventMailControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailComposerControllerIntegrationTest.java` (new)
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailComposerSendIntegrationTest.java` (new)
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/compose/PlaceholderScannerTest.java` (new)
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/compose/SpeakerAudienceAdapterTest.java` (new)
- `bruno-tests/events/mails-preview-newsletter.bru`, `mails-preview-invalid.bru`, `mails-preview-forbidden.bru` (new)
- `docs/architecture/06-backend-architecture.md`, `docs/user-guide/features/event-mail.md`
- `_bmad-output/implementation-artifacts/16-8-shared-composer.md`, `sprint-status.yaml`

16.8b:
- `web-frontend/src/services/mailMessageService.ts`, `web-frontend/src/hooks/useMailMessages/useMailMessages.ts`
- `web-frontend/src/components/organizer/EventPage/communications/composer/` (new): `MailComposer.tsx`,
  `ComposerFooter.tsx`, `ComposerPreview.tsx`, `ComposerRecipients.tsx`, `types.ts`,
  `audiences/{index,newsletter,registrants,speakers,venue,speakerTemplates,useVenueCoordinationConfig}.ts`,
  `audiences/{NewsletterFields,SpeakerFields,VenueFields,VenueRecipientFields}.tsx`,
  `__tests__/MailComposer.test.tsx`
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`,
  `communications/MailSentList.tsx`, `VenueCoordinationComposer.tsx`
- `web-frontend/src/components/organizer/EventPage/__tests__/EventCommunicationsContainer.test.tsx`,
  `communications/__tests__/MailSentList.test.tsx`
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json`
- `web-frontend/e2e/organizer/event-mail-composer.spec.ts`, `event-mail-composer-send.spec.ts` (new);
  `event-newsletter-send.spec.ts` (deleted)
- `docs/user-guide/features/event-mail.md`

## Change Log

- 2026-11-01: Story drafted from Epic 16, grounded in code at 15a13959. Split into 16.8a/b/c.
- 2026-11-01: Part 16.8a implemented (backend composer API, adapters, test send, progress, retry); in review.
- 2026-11-01: Part 16.8b implemented (shared composer UI, highlight, retry, show test sends); in review.
