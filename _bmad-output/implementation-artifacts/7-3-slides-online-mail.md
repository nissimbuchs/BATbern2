# Story 7.3: "The Slides Are Online" Mail

Status: ready-for-dev

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

- [ ] **Task 1: Seed the task template** (AC: 1)
  - [ ] New forward migration (current highest **V108**; next free at implementation time) inserting into `task_templates`: `name='Newsletter: Slides Are Online'`, `trigger_state` = the post-event state (e.g. `event_completed`/`event_live` — match the existing newsletter templates' convention), `due_date_type='relative_to_event'`, `due_date_offset_days = +14` (after the event), `is_default=true`. Mirrors the existing seeds in `V22` (e.g. "Newsletter: Speaker Lineup", offset −30).
  - [ ] Verify `EventTaskService.autoCreateTasksForState(...)` / `createTasksForEvent(...)` picks it up with **no code change** (templates are treated uniformly).
- [ ] **Task 2: `slides-online` email template (DE+EN)** (AC: 2, 4)
  - [ ] Add `services/event-management-service/src/main/resources/email-templates/slides-online-de.html` + `-en.html` (subject in the leading `<!-- subject: ... -->` comment; body uses `{{variable}}`; layout `batbern-default`). `EmailTemplateSeedService` auto-seeds on startup.
  - [ ] Update `EmailTemplateSeedService.deriveCategory(...)` so `slides-online` maps to `NEWSLETTER` (it currently falls through to `LAYOUT` — verify and fix).
- [ ] **Task 3: Send to event registrants** (AC: 2, 3, 4)
  - [ ] The existing `NewsletterEmailService.sendNewsletter(...)` sends to the **global** `newsletter_subscribers` pool. Add a recipient-source mode (or an overload) that targets **event registrants**: `registrationRepository.findByEventIdAndStatus(eventId, 'registered'|'confirmed')` → resolve email (`attendee_email` or `UserApiClient.getEmailByUsername`) → resolve locale from the attendee's language pref (Task 4) → skip opt-outs (Task 3a).
  - [ ] **Task 3a (opt-out):** for each registrant email, skip if a `newsletter_subscribers` row exists with `unsubscribed_at` or `suppressed_at` set. (Registrations have no own opt-out flag — reuse the newsletter suppression list.)
  - [ ] Reuse the async paged-send + per-recipient audit + 70ms SES throttle already in `NewsletterEmailService`.
- [ ] **Task 4: Per-recipient locale** (AC: 4)
  - [ ] Resolve the attendee's language from their profile (`UserApiClient` → user `pref_language` / preferred language). Map: `de*`→`de`, `en`→`en`, else → **`de` (German fallback)**. ⚠️ If the preferred-language field is not yet exposed on the user lookup response, expose it (small addition) — see Dev Notes; do not hardcode a single language for everyone.
- [ ] **Task 5: Double-send guard** (AC: 5)
  - [ ] Reuse `NewsletterEmailService`'s in-progress guard (`DuplicateNewsletterSendException`) and add a check that a completed slides-online send for this event doesn't re-fire (e.g. a `newsletter_sends` row with this template key + event, or a `notifications` guard).
- [ ] **Task 6: Endpoint** (AC: 2)
  - [ ] Prefer reusing `POST /api/v1/events/{eventCode}/newsletter/send` (`@PreAuthorize("hasRole('ORGANIZER')")`) with `templateKey="slides-online"` + a recipient-source flag = `registrants`. If a flag doesn't fit cleanly, add a sibling endpoint; keep it organizer-only.
- [ ] **Task 7: Tests (TDD) + doc-drift** (AC: 7)
  - [ ] Integration test (mocked `EmailService`, see `TestAwsConfig` `@Primary` mock): task auto-created with due = eventDate+14; send → active registrants only; opt-out skipped; DE/EN/German-fallback; double-send guard; per-recipient failure isolation. Clean up registrations/sends/notifications rows.
  - [ ] Update `.github/doc-drift-mappings.yml` targets if task-template/newsletter docs are mapped (or `[no-doc]`).

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

### Debug Log References

### Completion Notes List

### File List

## Resolved Decisions

_Resolved with the PM 2026-06-10._

1. **Trigger:** No automated materials-publish hook (none exists). A **default organizer task** "Newsletter: Slides Are Online" is auto-created with the event's task set, due **~2 weeks after** the event; the organizer **manually sends** the mail. Reuses the existing task-template + newsletter-send machinery.
2. **Recipients:** the event's **active registrants** (`registered`/`confirmed`), honouring the global email opt-out — **not** the newsletter-subscriber pool. (This requires extending the newsletter send with a registrants recipient source — the one real build beyond assembly.)
3. **Locale:** the attendee's **web-language preference**; `de*`→German, `en`→English, **else German fallback** (intentionally German, not EN).

## Open Questions

1. **Is the due date 2 weeks *after* the event (not before)?** Slides go up after the event, so the task should fall due ~14 days *after* the event date (offset `+14`). The original phrasing was ambiguous ("two weeks … of the event"). The story assumes **+14 after**; please confirm so the seed offset sign is right.
2. **Reuse the existing newsletter-send endpoint, or a dedicated slides-online send?** The cleanest reuse is the existing `POST …/newsletter/send` with `templateKey=slides-online` plus a "recipients = event registrants" flag. If you'd rather keep the registrant-targeted send fully separate from the subscriber newsletter (different audit, different metrics), say so — it changes whether we extend `NewsletterEmailService` or add a sibling service.
