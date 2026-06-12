# Story 7.3: "The Slides Are Online" Mail

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **attendee who registered for an event**,
I want an email when the slides go online,
so that I get the one post-event message I'll actually open — turning 3 website visits/year into 6 without asking more commitment of me.

**Source:** Brainstorming 2026-06-06 idea #07 (GitHub #752, 3 votes). Epic FR8–FR9. Cadence-match guardrail #24: event-triggered, never a recurring feed.

> **Design (PM-resolved 2026-06-10):** NOT an automated materials-publish trigger. Instead, an **organizer task** is auto-created with the event's standard task set, due ~2 weeks **after** the event (when slides are up); the organizer **manually sends** a newsletter-style email to the event's **active registrants** using a NEW `slides-online` template. This drops cleanly into the existing task-template + newsletter-send machinery — no new scheduler, no new domain event.

## Acceptance Criteria

1. A new **default task template** "Newsletter: Slides Are Online" is seeded so every event auto-gets a task (due ~14 days after the event date) prompting the organizer to send the slides-online mail — created via the **existing** `EventTaskService` auto-creation (no change to the task engine).
2. Sending reuses the **existing newsletter send** path with a NEW `slides-online` template (DE + EN only — do NOT create 10-locale templates); recipients are the event's **active registrants** (status `registered`/`confirmed`), **not** the global newsletter-subscriber pool.
3. Recipients with a global email opt-out (newsletter `unsubscribed_at`/`suppressed_at` for their email) are **excluded**.
4. Per recipient, the template language = the attendee's web-language preference: starts with `de` ⇒ German, `en` ⇒ English, **anything else / unknown ⇒ German fallback** (note: deliberately German, not EN, for this feature).
5. The send is guarded against **double-send** for the same event (reuse the in-progress guard + a slides-online-already-sent check).
6. A transient SES failure for one recipient is isolated (logged/marked failed) without aborting the rest.
7. Integration tests (PostgreSQL, **mocked EmailService** — no real SES) cover: task auto-created with correct due date; send goes to active registrants only; opt-out excluded; DE/EN/German-fallback selection; double-send guard; per-recipient failure isolation. No real outbound mail; no leftover data.

## Tasks / Subtasks

- [x] **Task 1: Seed the task template** (AC: 1)
  - [x] New forward migration **V110** (`V110__seed_slides_online_task_template.sql`) inserting into `task_templates`: `name='Newsletter: Slides Are Online'`, `trigger_state='event_completed'`, `due_date_type='relative_to_event'`, `due_date_offset_days = 1` (**+1 day AFTER the event** per Nissim's resolution, revised from the original `+14`), `is_default=true`, guarded by `NOT EXISTS`. Mirrors the V22 newsletter seeds.
  - [x] Verified `EventTaskService.createTasksForEvent(...)` picks it up with **no code change** — covered by `SlidesOnlineIntegrationTest` AC1 (asserts the task auto-created at `pending` with due = eventDate+1, trigger `event_completed`).
- [x] **Task 2: `slides-online` email template (DE+EN)** (AC: 2, 4)
  - [x] Added `email-templates/slides-online-de.html` + `-en.html` (subject in the leading `<!-- subject: ... -->` comment; `{{eventTitle}}/{{eventNumber}}/{{eventType}}/{{eventDate}}/{{eventDetailLink}}/{{currentYear}}`; layout `batbern-default`). Auto-seeded by `EmailTemplateSeedService` on startup.
  - [x] Updated `EmailTemplateSeedService.deriveCategory(...)` — added an explicit `slides-online → NEWSLETTER` case (it does not carry the `newsletter-` prefix, so it previously fell through to `LAYOUT`).
- [x] **Task 3: Send to event registrants** (AC: 2, 3, 4) — **dedicated sibling, not bolted onto the subscriber newsletter** (Nissim's resolution)
  - [x] New `SlidesOnlineEmailService` targets **event registrants** via `registrationRepository.findByEventIdAndStatusIn(eventId, ['registered','confirmed'])` → resolve email (`attendeeEmail`, fallback `UserApiClient.getEmailByUsername`) → resolve per-recipient locale (Task 4) → skip opt-outs (Task 3a). Dedupes by lowercased email.
  - [x] **Task 3a (opt-out):** skips any registrant whose email matches a `newsletter_subscribers` row with `unsubscribed_at` or `suppressed_at` set (new `findByEmailIgnoreCase`). Registrations have no own opt-out flag.
  - [x] Reuses the proven `newsletter_sends`/`newsletter_recipients` audit (discriminated by `template_key='slides-online'`), the 70 ms SES throttle, per-recipient audit + failure isolation; orphan recovery is already handled by `NewsletterEmailService.recoverOrphanedSends()` (template-agnostic).
- [x] **Task 4: Per-recipient locale** (AC: 4)
  - [x] `UserApiClient.getPreferredLanguage(username)` (new) reads `preferences.language` via `GET /users/{username}?include=preferences` — the field was **already exposed** on the user lookup (no users-api spec change needed). Lenient (null on any failure). Map: `de*`→`de`, `en`→`en`, else/unknown → **`de` (German fallback)**.
- [x] **Task 5: Double-send guard** (AC: 5)
  - [x] In-progress guard reuses `DuplicateNewsletterSendException` (scoped to `template_key='slides-online'` so it doesn't collide with a subscriber-newsletter send); a completed/partial slides-online send re-fire throws the new `SlidesOnlineAlreadySentException` (also 409, with an explicit `GlobalExceptionHandler` entry so the catch-all doesn't shadow it into a 500).
- [x] **Task 6: Endpoint** (AC: 2)
  - [x] Dedicated `POST /api/v1/events/{eventCode}/slides-online/send` (`@PreAuthorize("hasRole('ORGANIZER')")`) in a new `SlidesOnlineController` — sibling endpoint per Nissim's resolution (kept isolated from the subscriber-newsletter endpoint). Added to `events-api.openapi.yml` + regenerated FE types.
- [x] **Task 7: Tests (TDD) + doc-drift** (AC: 7)
  - [x] `SlidesOnlineIntegrationTest` (PostgreSQL, mocked `EmailService` + `UserApiClient`): task auto-created due eventDate+1; send → active registrants only; opt-out skipped; DE/EN/German-fallback (subject-language asserted); double-send guard (409 in-progress + already-sent); per-recipient failure isolation (PARTIAL). `SlidesOnlineEmailServiceTest` (Mockito) covers locale mapping, both guards, opt-out skip, queued response. 13 new tests green; 79 related-suite regression tests green.
  - [x] `.github/doc-drift-mappings.yml` has **no** entries mapped to the changed paths (newsletter / task-template / email-templates / UserApiClient / GlobalExceptionHandler) → **`[no-doc]`** applies to the commit.

## Dev Notes

### Existing machinery to REUSE (this is mostly assembly, not new infra)
- **Task system:** `EventTask` (`event_tasks`, V22) + `TaskTemplate` (`task_templates`, V22, with `due_date_type` / `due_date_offset_days`). `EventTaskService.createTasksForEvent(...)` (~L66) instantiates templates at event creation (status `pending`); `autoCreateTasksForState(...)` (~L116) flips them to `todo` on the trigger state; `calculateDueDate(...)` (~L182) does `eventDate.plus(offsetDays)`. **Seeding a new default template is all that's needed — no engine change.** Existing newsletter task seeds in `V22` (~L60–68) are the exact precedent (e.g. "Newsletter: Final Agenda", `relative_to_event`, −14).
- **Newsletter send:** `NewsletterController` `POST /api/v1/events/{eventCode}/newsletter/send` (~L244, ORGANIZER); `NewsletterEmailService.sendNewsletter(...)` (~L254) → async `executeNewsletterSendAsync(...)` (~L326): paged 50/batch, 70ms throttle, `newsletter_sends` audit + progress, per-recipient `newsletter_recipients`, `DuplicateNewsletterSendException` in-progress guard. **NOTE the recipient pool is global `newsletter_subscribers` — Task 3 changes the source to event registrants.**
- **Templates:** `EmailTemplateService.findByKeyAndLocale(...)` (~L61) + `mergeWithLayout(...)` (~L162); `EmailTemplateSeedService` auto-seeds `email-templates/{key}-{de|en}.html` on startup (filename pattern `^(?:(layout)-)?(.+)-(de|en)\.html$`), subject from `<!-- subject: ... -->`. `deriveCategory(...)` (~L151) needs a `slides-online`→`NEWSLETTER` case.
- **Registrants:** `RegistrationRepository.findByEventIdAndStatus(eventId, status)` (~L80); `Registration.attendeeEmail` denormalized, `attendeeUsername` for the profile lookup. Active = `registered`/`confirmed`.
- **Opt-out:** registrations have **no** own opt-out flag — reuse `newsletter_subscribers.unsubscribed_at`/`suppressed_at` matched by email.
- **Test SES mock:** `TestAwsConfig` `@Bean @Primary EmailService` Mockito mock — tests never hit SES.

### Locale dependency
- The attendee's language lives as a user preference (`pref_language` in company-user-management; web language menu persists it). The generated `UserResponse` used here may **not** expose `preferredLanguage` yet — if absent, add it to the user lookup (small) so per-recipient DE/EN/German-fallback works. Do NOT ship a single hardcoded language for all.

### Constraints / gotchas
- **DE+EN templates only**; **German fallback** for this feature (AC4) — note this intentionally differs from the platform's usual non-DE/EN→EN email fallback.
- **Staging = production:** no real outbound email in tests; reuse the mocked `EmailService`; clean up. The send already throttles for SES rate limits.
- **No @Retryable** for SES — per-recipient failures are caught and marked failed (match `NewsletterEmailService`); no blocking retry in a transaction.
- Near-zero organizer effort: the task is the only nudge; sending is one click on a pre-filled template.

### Project Structure Notes
- Template files in `.../resources/email-templates/`; task-template seed + any guard column in `.../resources/db/migration/`; send logic in `.../service/NewsletterEmailService.java` (extend) and `.../controller/NewsletterController.java`.

### References
- [Source: docs/prd/epic-7-attendee-experience-enhancements.md#story-73-the-slides-are-online-mail]
- [Source: services/event-management-service/.../service/EventTaskService.java] [Source: .../domain/TaskTemplate.java] [Source: .../resources/db/migration/V22__Add_task_system.sql]
- [Source: services/event-management-service/.../service/NewsletterEmailService.java] [Source: .../controller/NewsletterController.java]
- [Source: services/event-management-service/.../service/EmailTemplateSeedService.java] [Source: .../service/EmailTemplateService.java]
- [Source: services/event-management-service/.../repository/RegistrationRepository.java]
- [Source: _bmad-output/project-context.md#i18n-localization]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story, 2026-06-10.

### Debug Log References

- Initial `endpoint_alreadySent_returns409` IT returned **500** not 409: `SlidesOnlineAlreadySentException`'s `@ResponseStatus(CONFLICT)` was shadowed by `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` (line ~1253). Fixed by adding an explicit `@ExceptionHandler(SlidesOnlineAlreadySentException.class)` (mirrors the existing `DuplicateNewsletterSendException` handler). `DuplicateNewsletterSendException` was already explicitly handled, which is why the in-progress guard mapped to 409 correctly.
- A pure-Mockito unit test for per-recipient failure isolation hit a `STRICT_STUBS`/`computeIfAbsent` interaction (a non-matching `doThrow` stub left `mail` null for the non-failing recipient — not reproducible in a Spring context). Removed it; AC6 is covered authoritatively by `SlidesOnlineIntegrationTest` against real PostgreSQL (PARTIAL, sent=1/failed=1) — which passes.

### Completion Notes List

- **Locale field was already exposed.** Task 4's flagged risk (preferredLanguage maybe not on the user lookup) did not materialise: `UserResponse.preferences.language` is already in `users-api.openapi.yml` and CUMS already maps it via `?include=preferences`. So **no cross-service spec change** — just a lenient `UserApiClient.getPreferredLanguage` reading it (cached under a `prefLang:` key to avoid colliding with the base-user cache entry).
- **Dedicated sibling, reusing audit infra.** Per Nissim's resolution, the send is a standalone `SlidesOnlineEmailService` + `SlidesOnlineController` — NOT a mode on `NewsletterEmailService`. It reuses the `newsletter_sends`/`newsletter_recipients` tables discriminated by `template_key='slides-online'` (distinct audit/metrics without a new table), the 70 ms SES throttle, and per-recipient failure isolation. The synchronous send core is `processSend(...)` (package-private, non-`@Async`) so tests drive it deterministically; the public `sendSlidesOnline` does the guards + audit row + `@Async` dispatch.
- **Task due = eventDate + 1 day** (`offset_days = 1`, trigger `event_completed`), revised from the story's tentative `+14` per Nissim.
- **No frontend work** in this story (Tasks 1–7 are backend-only; ACs make no FE demand). The OpenAPI path + regenerated TS types are committed for contract-first completeness; wiring the organizer "send" button is out of scope.
- **Tests:** 13 new (5 unit + 8 PostgreSQL integration) all green; 79 related-suite regression tests (newsletter, email-template-seed, UserApiClient consumers, self-nomination) green. Full event-management suite NOT run end-to-end here (known to exceed the 20-min Testcontainers timeout on this machine — see memory `project_event_management_prepush_timeout`); changes to shared classes are additive and regression-covered at their call sites.
- **NOT committed** — left for review.

### File List

**New:**
- `services/event-management-service/src/main/resources/db/migration/V110__seed_slides_online_task_template.sql`
- `services/event-management-service/src/main/resources/email-templates/slides-online-de.html`
- `services/event-management-service/src/main/resources/email-templates/slides-online-en.html`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SlidesOnlineEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SlidesOnlineController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SlidesOnlineSendResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/exception/SlidesOnlineAlreadySentException.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SlidesOnlineEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SlidesOnlineIntegrationTest.java`

**Modified:**
- `services/event-management-service/src/main/java/ch/batbern/events/service/EmailTemplateSeedService.java` (deriveCategory: `slides-online → NEWSLETTER`)
- `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (handler for `SlidesOnlineAlreadySentException` → 409)
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` (+ `getPreferredLanguage`)
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` (impl + cache key)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSendRepository.java` (+ template-scoped guard queries)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSubscriberRepository.java` (+ `findByEmailIgnoreCase`)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java` (+ `findByEventIdAndStatusIn`)
- `docs/api/events-api.openapi.yml` (+ `/events/{eventCode}/slides-online/send` path + `SlidesOnlineSendResponse` schema)
- `web-frontend/src/types/generated/events-api.types.ts` (regenerated)
- `_bmad-output/implementation-artifacts/7-3-slides-online-mail.md` (this story)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (status → review)

### Change Log

- 2026-06-10 — Implemented Story 7.3 (dev-story, Claude Opus 4.8 1M): V110 task-template seed (+1 day, `event_completed`); DE+EN `slides-online` templates + `deriveCategory` NEWSLETTER case; dedicated `SlidesOnlineEmailService` + `SlidesOnlineController` (`POST /events/{eventCode}/slides-online/send`, ORGANIZER) sending to active registrants with per-recipient locale (de*/en/else→de German fallback), global opt-out exclusion, double-send guards, per-recipient failure isolation, reusing the newsletter audit/throttle infra; `UserApiClient.getPreferredLanguage`; OpenAPI path + schema + regenerated FE types. 13 new tests + 79 regression tests green. Status → review. `[no-doc]`.

### Open-Question Resolutions (Nissim, 2026-06-10, pre-code)

1. **Task due date → `+1` (one day AFTER the event).** The auto-created organizer task
   "Newsletter: Slides Are Online" is due **eventDate + 1 day**. Seed: `trigger_state='event_completed'`,
   `due_date_type='relative_to_event'`, `due_date_offset_days = 1`. (Revised down from the story's
   tentative `+14` assumption — organizer wants the nudge to surface promptly once the event completes.)
2. **Send path → DEDICATED SIBLING, fully isolated from the subscriber newsletter.** A new
   `SlidesOnlineEmailService` + `SlidesOnlineController` (`POST /api/v1/events/{eventCode}/slides-online/send`,
   ORGANIZER-only) — NOT an extra mode bolted onto `NewsletterEmailService`/the subscriber-newsletter
   endpoint. It REUSES the proven infra (the `newsletter_sends`/`newsletter_recipients` audit tables —
   discriminated by `template_key='slides-online'`, the 70 ms SES throttle, paged send, per-recipient
   failure isolation, `EmailService`/`EmailTemplateService`) but keeps audit/metrics/guard scoped to the
   slides-online template so the two sends never interfere.

## Resolved Decisions

_Resolved with the PM 2026-06-10._

1. **Trigger:** No automated materials-publish hook (none exists). A **default organizer task** "Newsletter: Slides Are Online" is auto-created with the event's task set, due **~2 weeks after** the event; the organizer **manually sends** the mail. Reuses the existing task-template + newsletter-send machinery.
2. **Recipients:** the event's **active registrants** (`registered`/`confirmed`), honouring the global email opt-out — **not** the newsletter-subscriber pool. (This requires extending the newsletter send with a registrants recipient source — the one real build beyond assembly.)
3. **Locale:** the attendee's **web-language preference**; `de*`→German, `en`→English, **else German fallback** (intentionally German, not EN).

## Open Questions

1. **Is the due date 2 weeks *after* the event (not before)?** Slides go up after the event, so the task should fall due ~14 days *after* the event date (offset `+14`). The original phrasing was ambiguous ("two weeks … of the event"). The story assumes **+14 after**; please confirm so the seed offset sign is right.
2. **Reuse the existing newsletter-send endpoint, or a dedicated slides-online send?** The cleanest reuse is the existing `POST …/newsletter/send` with `templateKey=slides-online` plus a "recipients = event registrants" flag. If you'd rather keep the registrant-targeted send fully separate from the subscriber newsletter (different audit, different metrics), say so — it changes whether we extend `NewsletterEmailService` or add a sibling service.

---

## Safety hardening + organizer UI (2026-06-12)

**Problem found (review):** the original 7.3 had NO organizer UI for the dedicated, safe
`/slides-online/send` (registrant-targeted) endpoint — and the `slides-online` template was
categorised `NEWSLETTER`, so it appeared in the Event → **Newsletter** tab's template picker.
Selecting it there and clicking Send would have **blasted the entire newsletter-subscriber pool**
(the subscriber send passed any `templateKey` straight through, no whitelist). The only reachable
action was the dangerous one.

**Fix (this change):**
1. **Backend reject (defence in depth):** `NewsletterEmailService.preview`/`sendNewsletter` now
   reject any `REGISTRANT_NOTICE`-category template with a 400 — the subscriber pool can never be
   sent a registrant template, regardless of UI.
2. **Recategorise:** `deriveCategory("slides-online") → REGISTRANT_NOTICE` (was `NEWSLETTER`), so it
   no longer appears in the subscriber-newsletter picker. Forward migration **V114** recategorises
   the already-seeded staging/prod rows (seeding is insert-only).
3. **Generalised, safe send + preview:** `SlidesOnlineEmailService` gained
   `sendRegistrantNotice(event, templateKey, sentBy)` + `previewRegistrantNotice(event, templateKey,
   locale)` (validates the template is `REGISTRANT_NOTICE`); `sendSlidesOnline` now delegates.
   New `RegistrantNoticeController`: `POST /events/{code}/registrant-notices/{preview,send}`
   (ORGANIZER). OpenAPI + regenerated FE types.
4. **New organizer tab "Registrant Notices"** (`EventRegistrantNoticesTab`, sibling of Newsletter):
   pick a `REGISTRANT_NOTICE` template + preview language, preview iframe (shows active-registrant
   count), send + confirm. The actual send still resolves each registrant's language (AC4); the
   selector is preview-only. i18n in all 10 locales.

**Tests:** `NewsletterEmailServiceTest` rejects a REGISTRANT_NOTICE template (preview + send);
`SlidesOnlineEmailServiceTest` (5) green with the new category guard; `EventRegistrantNoticesTab`
test (4). Backend `compileJava`/`compileTestJava` + FE `tsc`/ESLint clean.
