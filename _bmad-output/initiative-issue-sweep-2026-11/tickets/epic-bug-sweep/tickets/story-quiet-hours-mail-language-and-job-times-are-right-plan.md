---
title: 'Quiet hours, mail language and job times are right'
type: 'bugfix'
ticket: '5'
created: '2026-11-15'
status: 'in-progress'
baseline_revision: 'b46b2b8d001755167368ad97266f5bb4a74560d5'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** (#1222 items 1, 2, 7; #1119 A3) `NotificationService.isInQuietHours` has its two branches swapped: an overnight window (22:00-07:00) never suppresses and a same-day window (09:00-17:00) suppresses all day; it also reads the server clock's zone (UTC on ECS). `MailPipeline` sends every locale other than `en` in German, though the rule is EN for every locale except German ones. 15 `@Scheduled(cron)` methods carry no `zone` and no `TZ` is set, so they run on UTC, 1-2 h before their comments say.

**Approach:** Fix the quiet-hours comparison in the recipient's time zone (their settings `timezone`, else Europe/Zurich) with an injected `Clock`; make the pipeline's locale choice `en` for every non-German locale (`de*` and `gsw*` German, owner 2026-11-15; blank stays German); put `zone = "Europe/Zurich"` on every cron in CUMS and EMS with a guard test per service that fails a cron without it.

## Boundaries & Constraints

**Always:** One locale rule: `MailPipeline` and `EmailLocales` agree (reuse one function, no second copy). Clock via a `Clock` bean (boundary; tests set it). Guard tests scan the compiled classes or sources for every `@Scheduled` with a `cron` (including multi-line annotations and `@Schedules`), and assert `zone` is `Europe/Zurich`; a positive-control fixture proves the guard fires. Touched mail tests assert through the `SesRecorder`, never `verify(emailService)` (C6, story 11 freezes that count).

**Never:** Change the cron expressions themselves (their comments already state Swiss times). Change `fixedRate`/`fixedDelay` jobs. Add a `TZ` env var to the task definitions (the code states the zone).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Overnight window, inside | 22:00-07:00, now 23:30 Zurich | suppressed | — |
| Overnight window, outside | 22:00-07:00, now 12:00 Zurich | sent | — |
| Same-day window, inside | 09:00-17:00, now 10:00 | suppressed | — |
| Same-day window, outside | 09:00-17:00, now 18:00 | sent | — |
| Window edge | 22:00-07:00, now exactly 22:00 / 07:00 | 22:00 suppressed, 07:00 sent | — |
| Zone | 22:00-07:00, clock 21:30 UTC in winter (22:30 Zurich) | suppressed | — |
| No window | start or end null | sent | — |
| Mail locale | `fr`, `it`, `ja` | EN template | — |
| Mail locale | `de`, `de-CH`, `gsw-BE`, null, blank | DE template | — |
| Cron without zone | any `@Scheduled(cron=…)` lacking `zone` | guard test fails | — |

</frozen-after-approval>

## Code Map

- `services/event-management-service/.../notification/NotificationService.java:~95-125` -- `shouldSend` / `isInQuietHours`: swapped branches, `LocalTime.now()`. Inject `Clock` (add a `Clock` bean in EMS config if none; `UploadConfig.java:26` builds `Clock.systemUTC()` inline, reuse one bean there).
- `.../notification/UserPreferences.java` + `.../client/impl/UserApiClientImpl.java` `toNotificationPreferences` (~300-317) -- add `timezone` from the settings (spec field `timezone` exists in `UserSettings`); `CumsContractHarness.userWithSettings` already takes overrides.
- `.../mailcenter/render/MailPipeline.java:31,47-60,79-80` -- `wanted = "en".equalsIgnoreCase(locale) ? "en" : DEFAULT_LOCALE` in `template` and `storedTemplate`; replace by the shared rule. `.../service/EmailLocales.java` holds today's correct rule (package-private in `service`): move it where both can use it without breaking the mail-core wall (`EmsModuleWallsTest`: mail core `mailcenter.render|variables` uses no other mail-center class; `service` may use mail core, so put it in `mailcenter/render` and have `EmailLocales` callers use it).
- Crons, CUMS: `watch/WatchPairingCleanupService.java:29`, `service/CognitoConfirmationResendJob.java:72`, `service/UploadExpiryJob.java:20`, `service/UserReconciliationService.java:82`. EMS: `scheduler/TaskDeadlineReminderScheduler.java:46`, `scheduler/SpeakerReminderScheduler.java:33`, `service/RegistrationCleanupService.java:92`, `service/EventWorkflowScheduledService.java:62,131,223`, `service/SessionQnaScheduledService.java:34`, `service/UploadExpiryJob.java:20`, `service/RegistrationResendService.java:68`, `service/QnaNotificationService.java:109`, `scheduled/PublishingScheduledService.java:56`. Re-grep: multi-line annotations may hide more; PCS has none today but check.
- Recipient language (#1119 A3): already uses the CUMS preference (`SpeakerReminderService.speakerLocale` ~336, `DeregistrationEmailService.resolveLocale` ~120). Prove it with a test through the `SesRecorder` (an `en` and a `fr` recipient get the English mail).
- Tests to look at: `NotificationServiceIntegrationTest` (new, story 4), `TaskReminderEmailServiceTest` (mocks `EmailService`: move to SesRecorder if this story touches it), MailPipeline tests (`src/test/.../mailcenter/render/`).
- Docs: `docs/architecture/06d-notification-system.md` (quiet hours), `06-backend-architecture.md` (scheduled jobs, mail locale rule), CLAUDE.md Localization rule already says EN fallback; `AutomaticTriggerAudienceGuardTest` lists scheduled methods (unchanged audiences).

## Tasks & Acceptance

**Execution:**
- [ ] RED: unit tests for every matrix row of quiet hours (fixed `Clock`), a pipeline test for the locale rows, guard tests per service with a fixture class that lacks `zone`
- [ ] NotificationService + Clock bean + timezone mapping
- [ ] shared locale rule used by MailPipeline and every `EmailLocales` caller
- [ ] `zone = "Europe/Zurich"` on every cron (CUMS, EMS)
- [ ] recipient-language mail IT through the SesRecorder (`fr` → EN, `gsw-BE` → DE)
- [ ] docs 06d and 06

**Acceptance Criteria:**
- Given any `@Scheduled` cron in CUMS or EMS, when the guard tests run, then each has zone Europe/Zurich, and the fixture without zone is reported.
- Given a recipient with language `fr`, when a template mail renders, then the SesRecorder holds the English version; `gsw-BE` gets German.

## Design Notes

Architecture note: modules touched: EMS notify core (L1), mail core (L1, `mailcenter/render`), platform config (Clock bean), EMS client, CUMS jobs. New dependency: none across modules (the locale rule moves into mail core, which services already use). No endpoints. Reused: `CumsContractHarness`, `SesRecorder`. Deviations: none.

## Verification

**Commands:**
- `./gradlew --max-workers=4 :services:event-management-service:test` then `:services:company-user-management-service:test` (one at a time, tee'd) -- green, including `EmsModuleWallsTest` and `AutomaticTriggerAudienceGuardTest`.
- `make check-test-ratchet` -- no RAISED line.

**Rack rules:** one heavy build at a time (never two Gradle/Vitest runs in parallel), Gradle `--max-workers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push; leave the changes in the working tree. A bug found on the way is fixed too and reported (initiative C9).
