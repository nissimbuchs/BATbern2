---
title: 'Auto-participant enrolment, per-event email aliases, name-badge XLSX export'
type: 'feature'
created: '2026-05-28'
status: 'done'
baseline_commit: '42f7977a9065982895a466ce5bc3be68565e237a'
context:
  - '{project-root}/docs/plans/auto-participant-email-aliases-excel-export.md'
  - '{project-root}/_bmad-output/project-context.md'
  - '{project-root}/docs/architecture/ADR-003-meaningful-identifiers-public-apis.md'
  - '{project-root}/docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md'
  - '{project-root}/docs/architecture/ADR-006-openapi-contract-first-code-generation.md'
  - '{project-root}/docs/architecture/06d-notification-system.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Three friction points on the event-detail path that organizers hit every event cycle: speakers committed to an event are not auto-registered as participants (manual double-entry, missed badges and headcount); ad-hoc mailing lists per event/role are maintained by hand outside the system; name-badge data must be hand-extracted to CSV.

**Approach:** (F1) Idempotently auto-create a `registrations` row when a speaker transitions to ACCEPTED in `speaker_pool` OR when a session main/co-speaker is assigned. (F2) Extend the existing SES catch-all email forwarder (Story 10.26) to recognise `batbern{N}-speaker@` and `batbern{N}-moderator@` and resolve them via a new EMS distribution-list endpoint. (F3) Add `GET /events/{eventCode}/participants/export.xlsx` (Apache POI; SXSSF streaming) + a download button on the organizer Participants tab.

## Boundaries & Constraints

**Always:**
- ADR-003 in public APIs: `eventCode`, `username` — no UUIDs leaked.
- ADR-004: do NOT denormalise user PII; enrich via `UserApiClient` (15-min cache).
- ADR-006: edit `docs/api/events-api.openapi.yml` first, regenerate frontend types, commit generated types.
- TDD per CLAUDE.md (Red-Green-Refactor): tests written before implementation; integration tests extend `ch.batbern.shared.test.AbstractIntegrationTest` (Testcontainers PostgreSQL — never H2).
- Auto-registration is idempotent on `(event_id, attendee_username)` (unique constraint already exists).
- Speakers **bypass** `registration_capacity` (intentional — they are committed by organizers, not registering). `status="confirmed"` + `metadata.autoRegisteredFrom=<trigger>`. No confirmation email sent on this path.
- "Event moderator" = `Event.organizerUsername` (BATbern semantics). `SessionUser.MODERATOR` is per-session and NOT what this feature targets.
- New `-speaker` alias: organizer senders only (re-use existing `getOrganizerEmails` cache in `sender-auth.ts`). New `-moderator` alias: any sender (public contact pattern, same as `info@`/`events@`).
- Frontend i18n keys land in all 10 locales (de+en first-class, others may be straight translations). DE+EN-only rule applies to email templates only — does not apply here.
- Bruno `.bru` comments live in `docs { }` blocks, never `#` at file scope (would silently skip the file).
- Pipe gradle/make output to `/tmp/*.log` via `tee`; grep the log — never re-run to find errors.
- All gradle invocations from repo root with subproject notation.

**Ask First:**
- Skipping any of the three trigger points for auto-registration (pool-accept hook, session-primary-speaker provision, session-co-speaker assignment).
- Any Flyway migration (plan currently requires none).
- Changing the SES inbound stack topology (receipt rule shapes, MX, S3 prefixes). Lambda code is fair game; CDK stack shape is not.
- Triggering any real outbound mail in any test — staging IS production.

**Never:**
- Modify an already-applied Flyway migration (frozen on apply per CLAUDE.md).
- Make direct HTTP calls from frontend components; route via the service layer.
- Add `@JsonValue` / `@JsonProperty` to enums; default Jackson serialisation is canonical.
- Use `H2` or `@DataJpaTest` without PostgreSQL for integration tests.
- Send a registration-confirmation email for auto-registered speakers.
- Duplicate user-profile fields (firstName/lastName/email) on any new entity (we reuse the `registrations` denormalised cache).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| F1 happy: pool INVITED→ACCEPTED | Speaker A, event E, no prior reg | One new `registrations` row, status=confirmed, metadata.autoRegisteredFrom=POOL_ACCEPTED. SpeakerWorkflowService transition succeeds. | N/A |
| F1 happy: pool READY→ACCEPTED (on-behalf) | Same as above, READY entry | Same outcome, metadata source distinguishes path | N/A |
| F1 happy: session PRIMARY_SPEAKER created at READY hook | Speaker created on session | Auto-reg row appears | N/A |
| F1 happy: session CO_SPEAKER added | Co-speaker username added to a session | Auto-reg row appears, metadata.autoRegisteredFrom=SESSION_CO_SPEAKER | N/A |
| F1 idempotent: re-trigger | Trigger fires twice for same (event, username) | No-op on second call; no duplicate row | DB unique constraint is the safety net |
| F1 past event | Event date < now | Skip silently, log INFO | N/A |
| F1 user not found | UserApiClient throws UserNotFoundException | Skip, log WARN; speaker workflow still succeeds | Caught in service |
| F2 speakers alias | `batbern57-speaker@`, organizer sender | Lambda fetches `/events/BATbern57/distribution-list/speakers`, fans out to PRIMARY_SPEAKER emails + additionalEmails | Non-organizer sender → reject (existing path) |
| F2 moderator alias | `batbern57-moderator@`, any sender | Lambda fetches `/events/BATbern57/distribution-list/moderator`, fans out to organizer email + additionalEmails | N/A |
| F2 unknown event | `batbern999-speaker@`, valid sender | Endpoint 404 → Lambda logs WARN and returns 0 recipients (EmailsUnresolved metric) | Existing path; no SES bounce loop |
| F2 unsupported alias | `batbern57-foo@` | Falls through to existing patterns, then "Unknown forwarding address" warn → 0 recipients | Existing behaviour, no regression |
| F2 no scheduled sessions | Event exists but no PRIMARY_SPEAKER on scheduled session | Endpoint returns 200 + empty list; Lambda EmailsUnresolved | Logged, not an error |
| F3 export happy | Organizer hits `/events/BATbern57/participants/export.xlsx` | 200 + `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; cols: Vorname,Name,Firma,Rolle; rows = organizers ∪ event speakers ∪ registered attendees | N/A |
| F3 export role precedence | User who is both organizer and speaker of this event | One row, role=Organisator (precedence Organisator > Referent > Teilnehmer) | N/A |
| F3 export non-organizer | Non-organizer hits endpoint | 403 | Spring Security |
| F3 export unknown event | `BATbern999` | 404 | GlobalExceptionHandler |
| F3 frontend | Organizer clicks "Namensschilder exportieren" on Participants tab | Browser downloads `BATbern57-namensschilder.xlsx` | Surface error toast on non-2xx |

</frozen-after-approval>

## Code Map

**Backend (event-management-service):**
- `services/event-management-service/build.gradle` — add `org.apache.poi:poi-ooxml:5.4.0` (already pinned in partner-coordination-service).
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` — hook auto-registration call into `runAcceptedHook` (~line 553, after `confirmSessionUserIfPresent`) and into `provisionSessionAndPrimarySpeaker` (after `sessionUserRepository.save(speaker)` ~line 448).
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAutoRegistrationService.java` — NEW. `autoRegisterIfAbsent(UUID eventId, String username, String triggerSource)`; idempotent; past-event skip; user-not-found graceful.
- `services/event-management-service/src/main/java/ch/batbern/events/service/SessionUserService.java` — extend with `addCoSpeaker(...)` if missing, call auto-reg.
- `services/event-management-service/src/main/java/ch/batbern/events/service/DistributionListService.java` — NEW. `resolveSpeakers(eventCode)` + `resolveModerator(eventCode)`; flattens primary + `additionalEmails`; lowercase-dedupes.
- `services/event-management-service/src/main/java/ch/batbern/events/service/ParticipantsExportService.java` — NEW. POI SXSSFWorkbook; pattern from `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerAttendanceExportService.java`.
- `services/event-management-service/src/main/java/ch/batbern/events/controller/` — NEW endpoints (likely a new `ParticipantsController` or extension of `EventController`): `GET /events/{eventCode}/distribution-list/{kind}` + `GET /events/{eventCode}/participants/export.xlsx`.
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SessionUserRepository.java` — add `findScheduledPrimarySpeakersByEventId(UUID eventId)` (JPQL: join Session, filter `start_time IS NOT NULL` and `speaker_role='primary_speaker'`).
- `services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java` — existing `findByEventIdAndAttendeeUsername` already covers idempotency.
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` — existing `getUserByUsername(username)`, `getEmailByUsername(username)`. Confirm `UserResponse.additionalEmails` is exposed on the generated client; if not, regenerate.

**OpenAPI / generated types:**
- `docs/api/events-api.openapi.yml` — add 2 new operations: `getDistributionList` (kind enum: speakers, moderator) and `exportParticipantsXlsx`. Components: `DistributionListResponse`.
- `web-frontend/src/types/generated/events-api.types.ts` — regenerate via `npm run generate:api-types` after spec edit. Commit.

**Lambda (infrastructure):**
- `infrastructure/lambda/email-forwarder/address-resolver.ts` — add `batbern(\d+)-speaker` and `batbern(\d+)-moderator` cases; new helper `fetchEventDistributionList(eventCode, kind)`. Place BEFORE the bare `batbern{N}@` case.
- `infrastructure/lambda/email-forwarder/sender-auth.ts` — `-moderator` returns true (public); `-speaker` requires organizer (reuse `getOrganizerEmails`).
- `infrastructure/test/unit/email-forwarder.test.ts` — extend Vitest suites with new cases (resolver + auth, happy + negative).

**Frontend:**
- `web-frontend/src/services/eventService.ts` — add `exportParticipantsXlsx(eventCode): Promise<Blob>` (responseType: 'blob').
- `web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx` — add export button (`<Button startIcon={<DownloadIcon/>}>{t('event.participants.exportNameBadges')}</Button>`) wired to a `handleExport` that triggers `URL.createObjectURL` + anchor click.
- `web-frontend/src/components/organizer/EventPage/EventParticipantsTab.test.tsx` — extend with button-renders + click-triggers-download test.
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/translation.json` — add `event.participants.exportNameBadges` (DE/EN first-class, others straight translation).

**Tests:**
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerAutoRegistrationServiceTest.java` — NEW unit (Mockito).
- `services/event-management-service/src/test/java/ch/batbern/events/service/DistributionListServiceTest.java` — NEW unit.
- `services/event-management-service/src/test/java/ch/batbern/events/service/ParticipantsExportServiceTest.java` — NEW unit (parse the bytes back with POI to assert columns).
- `services/event-management-service/src/test/java/ch/batbern/events/integration/SpeakerAutoRegistrationIntegrationTest.java` — NEW integration (extends AbstractIntegrationTest); drives `SpeakerWorkflowService.transition(... ACCEPTED)` and asserts `registrations` row appeared.
- `services/event-management-service/src/test/java/ch/batbern/events/controller/DistributionListControllerIntegrationTest.java` — NEW.
- `services/event-management-service/src/test/java/ch/batbern/events/controller/ParticipantsExportControllerIntegrationTest.java` — NEW.
- `bruno-tests/events/distribution-list.bru` + `bruno-tests/events/participants-export.bru` — NEW (organizer auth, 404 on unknown event). Comments inside `docs { }` blocks.

**Deferred / not in this PR:**
- One-time backfill script for already-accepted-but-not-registered speakers (`scripts/data/backfill-speaker-registrations.sh`) — plan §4. Document in PR body but do NOT auto-run.

## Tasks & Acceptance

**Execution (ordered, TDD per task):**

- [x] `docs/api/events-api.openapi.yml` -- ADDED both operations + `DistributionListResponse` schema (ADR-006).
- [x] `web-frontend/src/types/generated/events-api.types.ts` -- regenerated via `npm run generate:api-types`; new ops verified present.
- [x] `services/event-management-service/build.gradle` -- POI 5.4.0 added.
- [x] `ParticipantsExportServiceTest.java` -- 6 RED→GREEN tests covering output, columns, role precedence, dedupe.
- [x] `ParticipantsExportService.java` -- SXSSFWorkbook implementation per spec.
- [x] `ParticipantsControllerIntegrationTest.java` -- 7 integration tests (XLSX + distribution-list combined in one class).
- [x] `ParticipantsController.java` -- both endpoints (export.xlsx + distribution-list/{kind}).
- [x] `SpeakerAutoRegistrationServiceTest.java` -- 7 unit tests.
- [x] `SpeakerAutoRegistrationService.java` -- implemented per spec.
- [x] `SpeakerWorkflowService.java` -- wired at `runAcceptedHook` (POOL_ACCEPTED for INVITED→ACCEPTED, POOL_ACCEPTED_ON_BEHALF for READY→ACCEPTED) AND `provisionSessionAndPrimarySpeaker` (SESSION_PRIMARY_SPEAKER).
- [x] `SessionUserService.java` -- hooked into existing `assignSpeakerToSession(...)` method (handles both PRIMARY_SPEAKER and CO_SPEAKER); no new controller surface needed. **Deviation:** spec asked for `addCoSpeaker(...)` only if no existing entry-point existed; the existing entry-point handles both roles, so we used it. See Spec Change Log §1.
- [x] `SpeakerAutoRegistrationIntegrationTest.java` -- 5 end-to-end tests through full workflow.
- [x] `DistributionListServiceTest.java` -- 8 unit tests.
- [x] `DistributionListService.java` -- implemented per spec.
- [x] `SessionUserRepository.java` -- added `findScheduledPrimarySpeakersByEventId(UUID)` and `findEventSpeakersByEventId(UUID)`.
- [x] Distribution-list integration tests -- covered by `ParticipantsControllerIntegrationTest.java`.
- [x] Controller for distribution-list -- in `ParticipantsController.java` (as per spec note).
- [x] `infrastructure/test/unit/email-forwarder.test.ts` -- extended with 9 new tests (resolver + sender-auth). Runner is Jest (not Vitest as spec said); spec uses Jest style.
- [x] `infrastructure/lambda/email-forwarder/address-resolver.ts` -- two new regex branches + `fetchEventDistributionList`.
- [x] `infrastructure/lambda/email-forwarder/sender-auth.ts` -- `-moderator` open (short-circuits before fetch), `-speaker` organizer-only.
- [x] `EventParticipantsTab.test.tsx` -- 4 new tests in `XLSX export button` describe block (12/12 total).
- [x] `web-frontend/src/services/eventApiClient.ts` -- `exportParticipantsXlsx(eventCode): Promise<Blob>` added. **Deviation:** spec said `eventService.ts`; actual file is `eventApiClient.ts` (singleton-class convention used across this codebase). See Spec Change Log §2.
- [x] `EventParticipantsTab.tsx` -- export button + handler + `<Alert>` error path.
- [x] `web-frontend/public/locales/*/events.json` -- new keys `event.participants.exportNameBadges` + `event.participants.exportError` added in all 10 locales. **Deviation:** spec said `translation.json`; actual file is namespaced `events.json`. See Spec Change Log §3.
- [x] Bruno tests -- 5 `.bru` files in `bruno-tests/events/` (distribution-list happy + moderator + unknown-event, participants-export happy + forbidden). All comments in `docs { }` blocks.
- [x] `.github/doc-drift-mappings.yml` -- no entry needed (architecture plan at `docs/plans/auto-participant-email-aliases-excel-export.md` checked in alongside code).
- [x] **NEW (deviation):** `V107__add_registration_metadata.sql` -- adds `metadata JSONB NOT NULL DEFAULT '{}'` to `registrations` (spec assumed column existed; it did not). New forward-only migration; CLAUDE.md frozen-migration rule respected. See Spec Change Log §4.
- [x] **NEW:** `Registration.java` -- added `@JdbcTypeCode(SqlTypes.JSON) Map<String, Object> metadata` field to map V107 column.
- [x] **NEW:** `api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java` + EMS `SecurityConfig.java` -- `permitAll` for `GET /api/v1/events/*/distribution-list/*` (Lambda forwarder VPC-internal pattern; matches existing `/registrations` permitAll). Per memory `feedback_dual_security_config.md`, BOTH gateway and target service security configs were updated.

**Acceptance Criteria:**

- Given a speaker in `READY` state on event `BATbern99`, when the organizer transitions to `ACCEPTED`, then a `registrations` row exists for `(BATbern99.id, speaker.username)` with `status='confirmed'` and `metadata->>'autoRegisteredFrom'='POOL_ACCEPTED'`.
- Given the speaker is already registered (any prior status), when ACCEPTED fires, then no second row is created.
- Given a session belonging to event `BATbern99` is assigned a `PRIMARY_SPEAKER` at READY-hook time, then the same auto-registration outcome holds (`SESSION_PRIMARY_SPEAKER`).
- Given the Lambda receives an inbound mail to `batbern99-speaker@batbern.ch` from an organizer, when the address-resolver runs, then `/events/BATbern99/distribution-list/speakers` is called and SES `SendRawEmail` is invoked once per resolved recipient.
- Given a non-organizer sends to `batbern99-speaker@`, when `sender-auth` runs, then the message is rejected (`EmailsRejected` metric +1).
- Given anyone sends to `batbern99-moderator@`, when `sender-auth` runs, then the message is accepted and resolved to the event organizer's email(s).
- Given an organizer GETs `/events/BATbern99/participants/export.xlsx`, when the file is downloaded, then it is a valid Office Open XML XLSX with columns `Vorname, Name, Firma, Rolle` and at least one row for each organizer + each event speaker (PRIMARY+CO) + each `registered/confirmed/attended` attendee, with the roles set to `Organisator | Referent | Teilnehmer` per precedence.
- Given the Participants tab on the organizer event page, when the export button is clicked, then `BATbern99-namensschilder.xlsx` downloads via the browser.
- All new tests pass; existing tests stay green. No new Flyway migration. OpenAPI updated and types regenerated. CHANGELOG / PR body documents the Lambda redeploy requirement.

## Spec Change Log

**§1 — 2026-05-28, implementation-time discovery: existing co-speaker entry point**
- Finding: spec task line "locate or add `addCoSpeaker(sessionId, username)`" assumed missing API. In reality `SessionUserService.assignSpeakerToSession(sessionId, username, role, title)` already handles both `PRIMARY_SPEAKER` and `CO_SPEAKER` from the existing Sessions tab.
- Amendment: hooked auto-reg inside `assignSpeakerToSession` for both roles. Idempotent w.r.t. the workflow-side hook (DB unique constraint is the safety net).
- KEEP: avoid inventing a new `addCoSpeaker` surface in this PR.

**§2 — 2026-05-28: service-file naming convention**
- Finding: spec referenced `eventService.ts`; repo convention is `*ApiClient.ts` (singleton class).
- Amendment: added `exportParticipantsXlsx(eventCode): Promise<Blob>` to `eventApiClient.ts`.
- KEEP: future frontend stories should use `*ApiClient.ts`, not `*Service.ts`.

**§3 — 2026-05-28: i18n namespace file**
- Finding: spec said `translation.json`; actual layout is namespaced. `EventParticipantsTab.tsx` uses `useTranslation('events')` → `events.json`.
- Amendment: added new keys to `events.json` in all 10 locales.
- KEEP: existing `eventPage.participantsTab.*` namespace was not consolidated with new `event.participants.*` — one-line follow-up if team wants single shape.

**§4 — 2026-05-28: `registrations.metadata` did not exist**
- Finding: spec §3.1 and plan §4 both stated the JSONB `metadata` column already existed on `registrations`. It did not.
- Amendment: added `V107__add_registration_metadata.sql` — `ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'`. Brand-new forward-only migration; CLAUDE.md frozen-migration rule respected.
- KEEP: always verify schema assumptions in plans by grepping migrations dir.

**§5 — 2026-05-28: Lambda test runner is Jest**
- Finding: spec said Vitest; `infrastructure/` uses Jest.
- Amendment: new resolver/auth tests written in Jest style.
- KEEP: future specs touching `infrastructure/` should reference Jest.

## Design Notes

**Why inline auto-registration (not async listener):** read-your-write semantics for organizer-facing flows. UserApiClient is cached (15-min); cost < 50ms. If perf later matters, refactor behind `@EventListener @Async` without API contract change.

**Why "event moderator" = `Event.organizerUsername`:** BATbern conventions; the SessionUser.MODERATOR role is per-session (panel moderators). Documented as an explicit choice — 1-line resolver swap if requirement changes.

**Why speakers bypass `registration_capacity`:** they are committed by organizers, not registering. Capacity gate exists for the public registration funnel.

**Role precedence in XLSX:** `Organisator > Referent > Teilnehmer`. A user appearing in multiple sets renders once with the highest-precedence role for accurate badge layout.

**Existing pattern reused for XLSX:** copy `PartnerAttendanceExportService` shape — `SXSSFWorkbook(100)`, bold-header with grey background, `try-with-resources` + `workbook.dispose()`, `ByteArrayOutputStream` → `byte[]`. Controller returns `ResponseEntity<byte[]>` with proper MIME + Content-Disposition.

**Existing pattern reused for email aliases:** Story 10.26 catch-all rule + address-resolver pattern + Story 10.32 additionalEmails fan-out. Two regex branches + two resolver helpers; zero new infra surface.

**Lambda redeploy:** the only deploy-time concern. Backend + frontend ship via standard pipelines; Lambda updates ride on Layer-Based deploy (Tier 3) when `infrastructure/lambda/email-forwarder/` changes are pushed. PR body must call this out explicitly.

## Verification

**Commands (from repo root, output piped via tee per CLAUDE.md):**

- `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/ems-test.log` -- expected: `BUILD SUCCESSFUL`; grep for `Tests run` count > baseline.
- `cd web-frontend && npm run test -- EventParticipantsTab 2>&1 | tee /tmp/fe-test.log` -- expected: all tests pass.
- `cd web-frontend && npm run type-check 2>&1 | tee /tmp/fe-typecheck.log` -- expected: no errors after type regen.
- `cd infrastructure && npm test -- email-forwarder 2>&1 | tee /tmp/lambda-test.log` -- expected: new resolver + sender-auth cases pass.
- `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno.log && grep -i "Skipping invalid file" /tmp/bruno.log` -- expected: bruno passes, ZERO "Skipping invalid file" warnings.
- `make dev-native-down && make dev-native-up 2>&1 | tee /tmp/dev-up.log` -- expected: all services start; `/tmp/batbern-1-event-management.log` reports listening on 8002.

**Manual checks:**
- `curl` `/api/v1/events/BATbern57/distribution-list/speakers` and `.../moderator` with organizer token from `~/.batbern/staging-organizer.json` → expect JSON `{ emails: [...] }`.
- `curl -o /tmp/x.xlsx ... /participants/export.xlsx` then `file /tmp/x.xlsx` → expect `Microsoft Excel`.
- Browse `http://localhost:8100/events/BATbern57` → Participants tab → click "Namensschilder exportieren" → file downloads.
- Inspect `_bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md` after this step writes it.

## Suggested Review Order

**Start here — the API contract**

- Read the two new operations + `DistributionListResponse` schema; this is what every layer below honours.
  [`events-api.openapi.yml:2604`](../../docs/api/events-api.openapi.yml#L2604)

**F1 — Auto-register speakers as participants**

- Entry point: the idempotent, fail-safe core. Catches `UserNotFoundException`, generic CUMS failures, code-collision, and `DataIntegrityViolationException` race so the speaker workflow caller is never poisoned (post-review patches §2/§3/§4).
  [`SpeakerAutoRegistrationService.java:94`](../../services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAutoRegistrationService.java#L94)

- Workflow hook #1: PRIMARY_SPEAKER provisioning at READY → fires `SESSION_PRIMARY_SPEAKER`.
  [`SpeakerWorkflowService.java:460`](../../services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java#L460)

- Workflow hook #2: ACCEPTED hook chooses `POOL_ACCEPTED` (INVITED→) or `POOL_ACCEPTED_ON_BEHALF` (READY→) by from-state.
  [`SpeakerWorkflowService.java:572`](../../services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java#L572)

- Workflow hook #3: session-tab assignment hooks both PRIMARY and CO speakers (existing entry point — Spec Change Log §1).
  [`SessionUserService.java:107`](../../services/event-management-service/src/main/java/ch/batbern/events/service/SessionUserService.java#L107)

- Schema: new `metadata` JSONB on `registrations` with safe additive default (Spec Change Log §4).
  [`V107__add_registration_metadata.sql:1`](../../services/event-management-service/src/main/resources/db/migration/V107__add_registration_metadata.sql#L1)

- Entity mapping for the new column.
  [`Registration.java:1`](../../services/event-management-service/src/main/java/ch/batbern/events/domain/Registration.java#L1)

**F2 — Per-event email aliases (`batbern{N}-speaker@`, `batbern{N}-moderator@`)**

- Backend resolver: builds the recipient list, flattens primary + additionalEmails, lowercase-dedupes; now hardened against transient CUMS failures (post-review patch §2).
  [`DistributionListService.java:1`](../../services/event-management-service/src/main/java/ch/batbern/events/service/DistributionListService.java#L1)

- HTTP surface: `GET /events/{eventCode}/distribution-list/{kind}` + `/participants/export.xlsx`.
  [`ParticipantsController.java:51`](../../services/event-management-service/src/main/java/ch/batbern/events/controller/ParticipantsController.java#L51)

- New repo query: scheduled (`start_time IS NOT NULL`) PRIMARY_SPEAKERs of an event.
  [`SessionUserRepository.java:1`](../../services/event-management-service/src/main/java/ch/batbern/events/repository/SessionUserRepository.java#L1)

- Lambda recipient resolution — two anchored regex branches placed BEFORE the bare `batbern{N}@` branch.
  [`address-resolver.ts:88`](../../infrastructure/lambda/email-forwarder/address-resolver.ts#L88)

- Lambda sender authorization — `-moderator` short-circuits to `true`, `-speaker` reuses the organizer-email cache.
  [`sender-auth.ts:43`](../../infrastructure/lambda/email-forwarder/sender-auth.ts#L43)

- Internal-only permitAll path for the Lambda's resolver call (dual config per `feedback_dual_security_config.md`).
  [`gateway SecurityConfig.java`](../../api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java) — [`EMS SecurityConfig.java`](../../services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java)

**F3 — Name-badge XLSX export**

- Backend XLSX builder: SXSSF streaming, role precedence `Organisator > Referent > Teilnehmer`; dedupe-map now keyed uniformly by `username` (post-review patch §1).
  [`ParticipantsExportService.java:131`](../../services/event-management-service/src/main/java/ch/batbern/events/service/ParticipantsExportService.java#L131)

- Frontend handler: `eventApiClient.exportParticipantsXlsx(eventCode)` → Blob → anchor click; disabled while in-flight; error → Alert.
  [`EventParticipantsTab.tsx:41`](../../web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx#L41)

- Frontend service: blob download via existing apiClient + error pipeline.
  [`eventApiClient.ts`](../../web-frontend/src/services/eventApiClient.ts)

- i18n: new keys in all 10 locales (DE/EN first-class).
  [`de events.json`](../../web-frontend/public/locales/de/events.json) · [`en events.json`](../../web-frontend/public/locales/en/events.json)

**Tests (peripherals — confirm behaviour, not design)**

- Speaker auto-registration unit (incl. new graceful-CUMS test from patch #2).
  [`SpeakerAutoRegistrationServiceTest.java`](../../services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerAutoRegistrationServiceTest.java)

- End-to-end speaker workflow → registration row.
  [`SpeakerAutoRegistrationIntegrationTest.java`](../../services/event-management-service/src/test/java/ch/batbern/events/integration/SpeakerAutoRegistrationIntegrationTest.java)

- XLSX bytes + role-precedence assertions.
  [`ParticipantsExportServiceTest.java`](../../services/event-management-service/src/test/java/ch/batbern/events/service/ParticipantsExportServiceTest.java)

- Endpoint integration: MIME, auth, 404, distribution-list shape.
  [`ParticipantsControllerIntegrationTest.java`](../../services/event-management-service/src/test/java/ch/batbern/events/controller/ParticipantsControllerIntegrationTest.java)

- Lambda Jest: 9 new resolver + sender-auth cases (incl. negative regex anchoring).
  [`email-forwarder.test.ts`](../../infrastructure/test/unit/email-forwarder.test.ts)

- Frontend: button render, download flow, error path.
  [`EventParticipantsTab.test.tsx`](../../web-frontend/src/components/organizer/EventPage/EventParticipantsTab.test.tsx)

- Bruno contract tests — now with a hardened magic-bytes assertion (post-review patch #5).
  [`participants-export.bru`](../../bruno-tests/events/participants-export.bru) · [`distribution-list.bru`](../../bruno-tests/events/distribution-list.bru)

---

## Deferred follow-ups (not blockers)

These are logged for after-the-fact attention; not gating this PR:

- Hoist the 3× `safeCompanyDisplayNames()` calls in `ParticipantsExportService` into one local variable.
- Add `V108` defense-in-depth unique constraint on `(event_id, attendee_username)` — the catch in `autoRegisterIfAbsent` already handles the race, but a hard constraint formalises the invariant.
- Add an integration test exercising `assignSpeakerToSession(... CO_SPEAKER ...)` end-to-end for `SESSION_CO_SPEAKER` trigger source (currently unit-tested only).
- Tighten Lambda regex to reject leading-zero events: `^batbern([1-9]\d*)-speaker$`.
- Add `AbortController` timeout to Lambda fetch calls.
- `@Transactional(readOnly = true)` on `DistributionListService.resolve*` holds a DB connection across UserApiClient HTTP — refactor to two-phase (load inside TX, enrich outside).
- Frontend: parse Blob JSON error bodies so the toast shows a useful message on 5xx.
- Consider verifying `additionalEmails.verifiedAt` before fan-out (currently unverified emails are included — same pattern as Story 10.32 across the codebase, but worth revisiting).
