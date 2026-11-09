---
title: 'Mail Center composer clean-up (16.8c)'
type: 'refactor'
ticket: '168c'
created: '2026-11-09'
status: 'ready-for-dev'
baseline_revision: ''
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
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
- [ ] Set `baseline_revision` and record the counts.
- [ ] RED: slides-online 409 and first-send ITs on `/mails`, plus 404 ITs for the retired paths.
- [ ] Spec deletions, then regenerate types (frontend and EMS build).
- [ ] EMS: delete controllers and methods, add internal return records, move `OwnText`, then the store shrinks to 0 lines (the rule stays frozen-empty).
- [ ] Frontend deletions, hooks, services, tests; the ratchet, type-copy and i18n baselines updated (shrink only).
- [ ] Bruno deletions, lint baselines and owners, docs.
- [ ] Full EMS suite, then the frontend suite, then the composer Playwright spec against local dev, and a local-dev send per audience (newsletter, registrant, speaker, venue) checked in `/dev/mails`.

**Acceptance Criteria:**
- Given the EMS store `f5e9936d…`, when the EMS suite runs, then it has no lines and the suite is green.
- Given `npm run lint:api` and `check:api-type-copies`, when run, then the baselines are smaller by the deleted lines and report no STALE or UNOWNED.
- Given local dev, when an organizer sends newsletter, registrant, speaker and venue mails from the composer, then each arrives in `/dev/mails`.

## Implementation Notes

## Plan Change Log

## Review Triage Log

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
