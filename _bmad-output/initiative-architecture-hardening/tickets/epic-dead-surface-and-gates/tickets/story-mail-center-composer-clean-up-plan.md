---
title: 'Mail Center composer clean-up (16.8c)'
type: 'refactor'
ticket: '168c'
created: '2026-11-09'
status: 'in-review'
baseline_revision: 'b13922c2fa7a7fe78b174d893999a393f56fb65f'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Since 16.8 every organizer send goes through the Mail Center composer (`/events/{eventCode}/mails`). The old per-audience compose endpoints are still in the specs and in EMS, and so are their services' preview, history, status and retry paths, their unmounted frontend components, hooks, services and Bruno files. That leaves two ways in per send, plus dead code that the gates still count.

**Approach:** Delete the retired operations and everything that exists only for them. Keep every send working through the composer. The deleted surface leaves the Spectral baseline, the 3c type-copy baseline and the EMS ArchUnit mail-center wall store. The slides-online one-shot 409 coverage moves onto `/mails`. Owner 2026-11-07: newsletter status and retry are retired too.

## Boundaries & Constraints

**Always:**
- No deferred work (owner 2026-11-09). Every failure that surfaces, and every defect found on the way, is fixed in the code in this PR, never worked around.
- Touched tests leave own-code mocks wherever the harness allows: SesRecorder, MSW, ContractServer.
- The composer sends for newsletter, registrants (template and own text), speakers, venue and partners stay green: their integration tests, and `e2e/organizer/event-mail-composer*.spec.ts`.
- Record before and after counts: Spectral baseline, type copies, EMS ArchUnit store lines, ratchet. Every one must shrink.
- `AutomaticTriggerAudienceGuardTest` stays green; scheduled and listener paths are unchanged.

**Never:**
- Do not remove a service method the composer, a scheduler or a listener still calls (`sendNewsletter`, `sendNewsletterOwnText`, `retryFailedRecipients`, `sendRegistrantNotice`, `sendRegistrantOwnText`, `VenueCoordinationService.send`/`sendOwnText`, `sendManualReminders`, `recoverOrphanedSends`, `processReminders`).
- Never edit an ArchUnit store or baseline to accept something; only let the store shrink by fixing code.
- Never touch the `mailcenter.notify-instead-of-forward` value.
- Do not change the partner `send-invite` endpoint in PCS (not 16.8c).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Retired path | organizer `POST /events/{c}/newsletter/send` (likewise preview, history, sends status and retry, registrant-notices preview/send, venue-coordination preview/send, speaker-pool send-reminder) | 404 | — |
| Slides-online in progress | `POST /events/{c}/mails` registrant audience, template slides-online, IN_PROGRESS send row | 409 | problem body |
| Slides-online already sent | the same with a COMPLETED row | 409 | — |
| Slides-online first send | no row | 2xx; the mail reaches SesRecorder after `awaitAsyncSends` | — |

</frozen-after-approval>

## Code Map

- Retired operations, with controller line in brackets:
  - `docs/api/event-newsletter-api.openapi.yml`: `sendEventNewsletter` :901 (NewsletterController :265), `previewEventNewsletter` :947 (:248), `getNewsletterHistory` :985 (:285), `getNewsletterSendStatus` :1017 (:299), `retryNewsletterSend` :1060 (:312).
  - `docs/api/event-newsletter-api.openapi.yml`: `previewRegistrantNotice` :801 and `sendRegistrantNotice` :850, both in `RegistrantNoticeController` (delete the class).
  - `events-core-api` :1951/:1996, `VenueCoordinationController` (delete the class).
  - `event-speakers-api` :1701 `sendSpeakerReminder`, `SpeakerReminderController` :48.
  - Delete their schemas once no kept operation refers to them.
- Services keep their send methods but return internal records instead of the deleted generated DTOs:
  - `NewsletterEmailService` :17-19 (also remove `preview` :220/229, `toStatusResponse` :667, `toResponse` :684)
  - `SlidesOnlineEmailService` :15-16 (remove `previewRegistrantNotice` :254)
  - `VenueCoordinationService` :4-5 (remove `preview` :93)
  - `SpeakerReminderService.sendManualReminder` :153 and `ManualReminderResult`
  - Check `getTestModeRecipientCount` :246 and the `testMode` parameter: remove them if only the old path used them.
- Composer: `mailcenter/EventMailController`, `mailcenter/compose/MailComposerService`, the `*AudienceAdapter` classes. `RegistrantAudienceAdapter` :138 calls `sendRegistrantNotice`.
- ArchUnit store `services/event-management-service/src/test/resources/archunit_store/f5e9936d-b4a3-4913-93f7-cba02fa75bbe` has 2 lines: `NewsletterEmailService.prepare` :740 and `SlidesOnlineEmailService.processSend` :329 both call `mailcenter.compose.OwnText.content`. Fix by moving `OwnText` to mail core (`mailcenter.render`), if it uses nothing of `compose`, or otherwise by restructuring the call. Update `module-map.md` "Left frozen after 17.5" accordingly. The `c1e3ecec` store belongs to 17.15; leave it alone.
- EMS tests:
  - Remove the old-path tests in `NewsletterControllerIntegrationTest` (keep the subscriber tests), `VenueCoordinationControllerIntegrationTest`, `SpeakerReminderControllerIntegrationTest` and `NewsletterEmailServiceTest` :375-408.
  - `SlidesOnlineIntegrationTest` :209-273 moves onto `/mails` (409 in progress, 409 sent, first send, 403, 404). Before deleting any old test, check that its coverage of a kept behaviour exists on the composer path; if not, move it there.
- Frontend (`web-frontend/src`):
  - Components to delete under `components/organizer/EventPage/`: `SpeakerBulkComms.tsx`, `EventRegistrantNoticesTab.tsx`, `EventNewsletterTab.tsx`, `VenueCoordinationComposer.tsx`, `EventVenueTab.tsx` (unmounted; exported in `index.ts:9`), and their tests.
  - Service code to delete: hooks `useNewsletter.ts` :147-208, `useRegistrantNotice`, `useSpeakerPool.ts` :213 `useSendReminder`; services `newsletterService.ts` :136-190, `registrantNoticeService.ts`, `venueCoordinationService.ts` (move the type `VenueRecipientRole` to its user, `useVenueCoordinationConfig.ts`), `speakerPoolService.ts` :162-168. Keep `subscribe` (`useRegistration.ts:10`).
  - Remove the matching lines from `tsconfig.test-harness.json` :23 and the ratchet baseline. i18n keys used only by deleted components go from all locales.
- Bruno: `events/venue-coordination-preview.bru`, `speaker-pool-api/61,63,64,65,66-send-reminder*.bru`.
- Lint:
  - Remove `api-lint-baseline.txt` lines 28-31, 56-57, 81-82, 97 and owner groups G14, G15.
  - Remove `api-type-copies-baseline.txt` (both lines) and owner group N1.
  - Remove `.spectral.yaml` :105 `send-reminder` if nothing else uses it.
- Docs: `06-backend-architecture.md` :711, :1202; `docs/user-guide/features/event-mail.md` :75; the 16.8 story AC18 status.

## Tasks & Acceptance

**Execution:**
- [x] Set `baseline_revision` and record the counts.
- [x] RED: slides-online 409 and first-send ITs on `/mails`, plus 404 ITs for the retired paths.
- [x] Spec deletions, then regenerate types (frontend and EMS build).
- [x] EMS: delete controllers and methods, add internal return records, move `OwnText`, then the store shrinks to 0 lines (the rule stays frozen-empty).
- [x] Frontend deletions, hooks, services, tests; the ratchet, type-copy and i18n baselines updated (shrink only).
- [x] Bruno deletions, lint baselines and owners, docs.
- [x] Full EMS suite, then the frontend suite, then the composer Playwright spec against local dev, and a local-dev send per audience (newsletter, registrant, speaker, venue) checked in `/dev/mails`.

**Acceptance Criteria:**
- Given the EMS store `f5e9936d…`, when the EMS suite runs, then it has no lines and the suite is green.
- Given `npm run lint:api` and `check:api-type-copies`, when run, then the baselines are smaller by the deleted lines and report no STALE or UNOWNED.
- Given local dev, when an organizer sends newsletter, registrant, speaker and venue mails from the composer, then each arrives in `/dev/mails`.

## Implementation Notes

Counts, before (b13922c2) → after:
- Spectral baseline (`api-lint-baseline.txt`, finding lines): 118 → 109 (the 9 retired operations); owner groups G14, G15 removed.
- Type copies (`api-type-copies-baseline.txt`): 2 → 0; owner group N1 removed.
- EMS ArchUnit store `f5e9936d…` (mail center wall): 2 → 0 lines; rule stays frozen-empty. `c1e3ecec` (17.15) untouched, 2 lines.
- Test ratchet: 659 → 644 lines, total count 825 → 802 (15 STALE lines locked in, none raised).
- i18n baseline: 0 frozen keys, unchanged; 81 (en/de) and 71 (other locales) keys of the deleted components removed from `events.json`, `organizer:newsletter.templateSelect` from all 10.

Done:
- Specs: 10 operations and their 18 schemas (incl. `VenueRole`, tag `Registrant Notice`) removed from `event-newsletter-api`, `events-core-api`, `event-speakers-api`; `send-reminder` dropped from the `.spectral.yaml` verb list; `VenueRole` import/schema mapping dropped from the EMS `build.gradle`; types regenerated.
- EMS: `RegistrantNoticeController`, `VenueCoordinationController` deleted; `NewsletterController` keeps the subscriber endpoints; `SpeakerReminderController` keeps the reminders toggle. Services return `QueuedSend(sendId, recipientCount)`; `NewsletterEmailService` lost preview, test mode, canary `maxRecipients`, status/response mapping and its `UserApiClient`; `SlidesOnlineEmailService` lost `previewRegistrantNotice`; `VenueCoordinationService` lost `preview` and returns nothing from `send`; `SpeakerReminderService` lost `sendManualReminder`, `validateSpeakerState` and their exceptions. Unused repository methods removed. `OwnText` moved to `mailcenter.render` (no compose dependency; `ComposeRequest.ownTextContent()` for the adapters); the layout key is now `MailContent.DEFAULT_LAYOUT_KEY`, `MailRenderer.LAYOUT_KEY` aliases it.
- Tests: `RetiredComposePathsIntegrationTest` (10× 404 plus positive control); slides-online 409/409/first send/403/404 on `/mails` (class no longer `@Transactional`, CUMS via `CumsContractHarness.userWithLanguage`); venue cases moved to `VenueComposerIntegrationTest`; manual-reminder unit tests now cover `sendManualReminders`, `manualAudience`, `deadlineFor`.
- Frontend: the five components, their tests, `useRegistrantNotice`, the send/history/status/retry/subscriber-count hooks and services, `useSendReminder`, `venueCoordinationService`, `registrantNoticeService` deleted.
- Bruno: the 6 files deleted; two non-sending composer previews added (`events/mails-preview-speakers-{invalid,missing}-type.bru`).
- Docs: module map, 06-backend, 01-system-overview, user guide event-mail, changelog.

Found on the way:
- The retired send-reminder endpoint answered 500 for a speaker without a resolvable address (`speaker_reminder_log.email_address` NOT NULL). Gone with the endpoint; the scheduler (`shouldSendReminder`) and the composer (skips `NO_EMAIL`) already guard it.
- The admin venue-contacts subtitle pointed at the removed Venue tab; now the Communications tab, all 10 locales.
- The newsletter duplicate-send guard took any IN_PROGRESS `newsletter_sends` row, so a registrant notice in progress made the composer refuse the newsletter with 409 (seen in the local-dev send per audience). Fixed test-first: the guard skips registrant-notice rows (own commit, `fix(mail)`), ITs for both directions in `SlidesOnlineIntegrationTest`.
- `event-mail-composer-send.spec.ts` (partners own text) compared captured mails with `deliveredCount`, which local dev never raises (no SES events), and read `cc` as a string though the capture sends an array. It passed only while every local partner address was reserved. Now compares with recipients minus failed, and `cc` as an array.

Verification (run, results in `~/.cache/batbern-test-logs/168c-*.log`):
- Full EMS suite: 2671 tests, 0 failed, 15 skipped; store `f5e9936d…` 0 bytes. Targeted re-run after the guard fix and the checkstyle line wraps: green.
- Vitest: 460 files, 5896 passed, 36 skipped. `type-check`, `lint:api`, `check:api-type-copies`, `check:test-ratchet`, `check:i18n-keys`, `check-frontend-api-paths`, `check:api-types` (regenerated = committed): green.
- Playwright `e2e/organizer/event-mail-composer*.spec.ts` against local dev: 12 passed (after the spec fix above).
- Local dev (BATbern60): the ten retired paths sampled (newsletter send, history, venue preview) answer 404. Composer sends: venue (one mail, To venue, Cc catering, captured), newsletter (1057 queued, 4 non-reserved addresses captured, the rest refused by the reserved-domain wall as designed), registrants slides-online and deregistration call (captured for the non-reserved address), speakers own text (all 4 local speaker addresses are reserved, so none captured; the organizer test send of the same mail was captured). No speaker in BATbern59–66 is in a reminder state locally, so the reminder template path was verified by the ITs only. Newsletter queued 37 ms after a registrant own-text send: 202 (before the fix: 409).
- Bruno: the two new `mails-preview-speakers-*` files and `mails-preview-invalid.bru` run against local dev: pass, no "Skipping invalid file".

Review pass 1 fixes (2026-11-10, see the Review Triage Log), each test-first where it is a bug:
- The newsletter guard covers PENDING and IN_PROGRESS (IT: a queued PENDING newsletter makes a second send answer 409).
- A reserved-domain venue contact: the mail center advice answers 422 (`details.code = RESERVED_DOMAIN`, the domain, not the address); saving `venue.coordination.config` refuses such a contact with 400. ITs for both.
- `GET /newsletter/subscribers/count`, `SubscriberCountResponse`, the controller method and its tests are removed.
- Touch rule, backend: `NewsletterSendIntegrationTest` and `ManualSpeakerReminderIntegrationTest` (real repositories, SesRecorder, CumsContractHarness) replace the mock-verify unit tests of the changed methods; `SlidesOnlineEmailServiceTest` is deleted, its per-registrant cancel link case moved to `SlidesOnlineIntegrationTest`, which now scopes its assertions and cleanup to its own event and addresses.
- Touch rule, frontend: `useNewsletter`, `useSpeakerPoolHooks`, `newsletterService`, `speakerPoolService` tests run on the MSW harness.
- Spec info text of `event-newsletter-api` and the 06-backend Retry bullet rewritten.
- Ratchet after the pass: 796 (from 802).

## Plan Change Log

- `VenueRecipientRole` not moved to `useVenueCoordinationConfig.ts`: `VenueRecipientFields` uses the composer's existing `VenueRole` (same type), so there is one name, not two.
- `GET /newsletter/subscribers/count` removed too (frontend hook and service, backend operation): only the removed newsletter tab used it (review pass 1).

## Review Triage Log

Pass 1 (quick lens, 2026-11-10): high 1, medium 5, low 3, false 0. All patches, under the owner's no-defer rule (2026-11-09).
- high, patch: the newsletter duplicate-send guard only checks IN_PROGRESS. A second send while the first is PENDING (queued, before `markInProgress`) mails every subscriber twice. Fix: the guard covers PENDING and IN_PROGRESS, with an IT.
- medium, patch: the venue send answers 500 when the stored contact is on a reserved domain (`ReservedEmailRecipientException` reaches the catch-all). Fix: a mail-center 4xx mapping with a clear message, plus validation of the venue config on write against reserved domains, with ITs.
- medium, patch: the dead `GET /newsletter/subscribers/count` (only the deleted tab used it). Fix: remove the operation, schema, controller method and tests; the baselines shrink.
- medium, patch, touch rule: the backend unit tests of changed methods (`SpeakerReminderServiceTest` ManualReminders, `NewsletterEmailServiceTest`, `SlidesOnlineEmailServiceTest`) assert via verify on own-code mocks. Fix: move the behaviour to ITs (SesRecorder, real repositories, CumsContractHarness) and delete the mock-verify tests they replace.
- medium, patch, touch rule: the frontend tests `useNewsletter`, `useSpeakerPoolHooks`, `newsletterService`, `speakerPoolService` still `vi.mock` own services. Fix: move them to the MSW harness; the ratchet shrinks.
- medium, patch: `SlidesOnlineIntegrationTest` asserts on the whole `newsletter_sends` table and deletes every simulator subscriber. Fix: scope it to the class's event and class-scoped addresses.
- low, patch: stale newsletter spec wording at L5 and L506.
- low, patch: the garbled Retry sentence at 06-backend L722.

## Design Notes

Work rules on rack:
- One heavy job at a time: never run a Gradle suite, Vitest, Playwright or a `git commit`/`git push` hook in parallel.
- Gradle runs with `--max-workers=4`, Vitest with `--maxWorkers=4`.
- Logs go to `~/.cache/batbern-test-logs/` via `tee`, with `set -o pipefail`.
- Commit on the current branch in conventional-commit steps, never `--no-verify`, never push.
- Services are managed only with `make dev-native-*`.

Architecture note:
- **Modules:** EMS mail center (L6) and mail core (L1, gains `OwnText`), the newsletter, registrant, venue and speaker services.
- **New dependencies:** none.
- **Endpoints:** none new; ten removed. The nearest kept operations are `/events/{eventCode}/mails`, its preview and its retry.
- **Reused:** `MailComposerService` and its adapters.
- **Deviations:** none.

Decisions by the agent under the owner's blanket go (2026-11-09):
- The old single-speaker manual reminder and the newsletter `maxRecipients`/`testMode` are not rebuilt in the composer. No mounted UI has used them since 16.8, and the composer's organizer test send (`target`) covers testing.
- `testMode` code that only served the old path is deleted.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/168c-ems.log`: expected BUILD SUCCESSFUL.
- `cd web-frontend && npm run type-check && npm run lint:api && npm run check:api-type-copies && npx vitest run --maxWorkers=4`: expected green.
- `make check-test-ratchet check-i18n-keys check-frontend-api-paths`: expected green.
