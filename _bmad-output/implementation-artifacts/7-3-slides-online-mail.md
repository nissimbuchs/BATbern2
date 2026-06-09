# Story 7.3: "The Slides Are Online" Mail

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **attendee who registered for an event**,
I want an email the day the slides go online,
so that I get the one post-event message I'll actually open — turning 3 website visits/year into 6 without asking more commitment of me.

**Source:** Brainstorming 2026-06-06 idea #07 (GitHub #752, 3 votes). Epic FR8–FR9. Cadence-match guardrail #24: event-triggered, never a recurring feed.

## Acceptance Criteria

1. When an event's slides/materials become published, each **registered** attendee of that event receives exactly **one** "slides are online" email.
2. The email is **DE or EN only** — German if the attendee's language preference starts with `de`, otherwise English; no language preference ⇒ EN.
3. The send is **idempotent per event**: if the triggering event re-fires (retry/re-publish), no duplicate emails.
4. A transient SES failure for one recipient is logged/handled without blocking the others.
5. Sending reuses the shared `EmailService` + `EmailTemplateService` pattern (Story 6.5); new templates `slides-online-de.html` / `slides-online-en.html` added (DE+EN only — do NOT create 10 locale templates).
6. Integration tests (PostgreSQL, **mocked EmailService** — no real SES) cover: one-email-per-registrant, DE/EN selection, idempotency, per-recipient failure isolation. No real outbound mail; no leftover data.

## Tasks / Subtasks

- [ ] **Task 0: Resolve the trigger (BLOCKING — see Open Question 1)** (AC: 1)
  - [ ] Decide what "slides are online" means: (a) a NEW `materials` publish phase added to `PublishingService`/`PublishingScheduledService`, or (b) a signal fired when an event's session materials first become public. There is **currently no `materials` phase** (only topic/speakers/agenda) and **no `EventMaterialsPublishedEvent`**. Pick one with PM/architect before coding.
- [ ] **Task 1: Fire a domain event on materials publish** (AC: 1, 3)
  - [ ] Per Task 0's decision, publish an `EventMaterialsPublishedEvent(eventCode)` at the chosen point (extend `PublishingService.publishPhase(...)` or the materials-visibility path).
- [ ] **Task 2: Notification listener** (AC: 1, 2, 4)
  - [ ] Add `onEventMaterialsPublished(...)` modeled on `NotificationService.onEventPublished(EventPublishedEvent)` (~L48–78): fetch registrants via `registrationRepository.findUsernamesByEventCode(eventCode)`, resolve email via `userApiClient.getEmailByUsername(...)`, resolve locale (de* → de else en), render `slides-online-{de|en}` template, send via `emailService.sendHtmlEmail(...)`.
- [ ] **Task 3: Idempotency** (AC: 3)
  - [ ] Reuse the `notifications` table (V33) — record a row per (recipient, eventCode, type=`SLIDES_ONLINE`) and guard with a uniqueness check (or add a unique constraint via a new forward migration; current highest **V108**, use next free number). Skip recipients already sent.
- [ ] **Task 4: Templates (DE+EN)** (AC: 5)
  - [ ] `services/event-management-service/src/main/resources/email-templates/slides-online-de.html` + `-en.html`, using the existing layout (`layout-batbern-default-{de|en}.html`) + `{{variable}}` substitution. Subject per template.
- [ ] **Task 5: Tests (TDD)** (AC: 6)
  - [ ] Integration test with mocked `EmailService` (see `TestAwsConfig` `@Primary` mock) asserting send count, locale, idempotency (second fire = 0 new sends), and that one recipient's failure doesn't abort the loop. Clean up notification/registration rows.
- [ ] **Task 6: Doc-drift** — update scheduler/notification docs per `.github/doc-drift-mappings.yml` in the same commit (or `[no-doc]` if pure internal).

## Dev Notes

### Existing machinery to REUSE
- **Listener pattern (the template to copy):** `services/event-management-service/.../notification/NotificationService.java` `onEventPublished(EventPublishedEvent)` (~L48–78) → `createAndSendEmailNotification(...)` (~L84–133): creates a `notifications` row, fetches email, sends, marks SENT/FAILED. **This is exactly the shape needed.**
- **Registrants:** `RegistrationRepository.findUsernamesByEventCode(eventCode)` (~L169–171).
- **Email + locale:** shared-kernel `EmailService.sendHtmlEmail(...)` (~L88) / `sendHtmlEmailSync` (~L124); `EmailTemplateService.findByKeyAndLocale(...)` (~L60) + `mergeWithLayout(...)` (~L162); model after `SpeakerReminderEmailService` (locale: `locale.getLanguage().equals("de") ? "de" : "en"` ~L153, DB-first template + classpath fallback).
- **User email/locale:** `UserApiClient.getEmailByUsername(username)` (~L80). ⚠️ **Language-pref dependency:** `UserResponse` may still default locale to German pending Story 10.15's `preferredLanguage` — verify whether the pref is available; if not, document the fallback (de) and wire the de*/en rule the moment the field exists.
- **Idempotency store:** `notifications` table — `V33__Create_notifications_table.sql` (status PENDING/SENT/FAILED). No unique constraint today.
- **Test SES mock:** `TestAwsConfig` provides `@Bean @Primary EmailService` Mockito mock (~L128–134) — tests never hit SES.

### Why there's a design gap (Open Question 1)
`PublishingScheduledService` auto-publishes only **speakers @30d** and **agenda @14d** (cron `0 0 1 * * *`); `PublishingService.publishPhase` sets `currentPublishedPhase` to topic/speakers/agenda. **Materials are per-session uploads, not a publish phase, and no domain event fires for them.** So this story must first define the trigger (Task 0).

### Constraints / gotchas
- **DE+EN only** for email (email-localization rule) — fallback EN; do NOT fan out 10 locales.
- **Staging = production:** no real outbound email in tests; reuse the mocked `EmailService`. Add cleanup.
- **No @Retryable** exists for SES today; failures are caught per-recipient and marked FAILED (match that — don't add a blocking retry loop inside a transaction).
- `PublishingScheduledService` currently has **no ShedLock** (multi-instance race risk) — if you add a scheduled element, add `@SchedulerLock` (see `EventWorkflowScheduledService`); otherwise prefer the event-listener path.

### Project Structure Notes
- Listener lives in `.../notification/`; templates in `.../resources/email-templates/`; migration in `.../resources/db/migration/`.

### References
- [Source: docs/prd/epic-7-attendee-experience-enhancements.md#story-73-the-slides-are-online-mail]
- [Source: services/event-management-service/.../notification/NotificationService.java#onEventPublished]
- [Source: services/event-management-service/.../service/SpeakerReminderEmailService.java] [Source: shared-kernel/.../service/EmailService.java]
- [Source: services/event-management-service/.../scheduled/PublishingScheduledService.java] [Source: .../service/publishing/PublishingService.java]
- [Source: _bmad-output/project-context.md#i18n-localization] [Source: #backend-integration-tests]

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Open Questions

1. **What exactly triggers the email — a new "materials published" phase, or session materials becoming visible?** Today the platform auto-publishes speakers and the agenda, but "slides/materials" are uploaded per session and have no publish moment or event. We need to decide whether to add a small "materials" publish step (organizer clicks publish, or it auto-publishes on a schedule) or to fire on the first session-material becoming public. This is the one real design decision in the story and it needs PM/architect sign-off before coding.
2. **Does the attendee's language preference actually reach this service yet?** The email must pick DE vs EN from the attendee's preferred language, but that field may not be exposed on the user lookup until a separate story lands. If it isn't available, the MVP will send EN (or DE) as a fallback and we wire the real preference once it exists — please confirm that interim behaviour is acceptable rather than blocking 7.3 on it.
3. **Should unsubscribed / cancelled registrants be excluded?** The plan emails everyone with a registration for the event. If someone cancelled, or globally opted out of email, we probably shouldn't mail them. Confirm whether to filter by registration status (e.g. only `registered`/`confirmed`) and honour any global email-opt-out flag.
