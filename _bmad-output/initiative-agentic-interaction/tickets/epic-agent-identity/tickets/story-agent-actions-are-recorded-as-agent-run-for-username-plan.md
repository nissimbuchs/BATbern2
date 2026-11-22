---
title: 'Agent actions are recorded as agent:<run> for <username>'
type: 'feature'
ticket: '2'
created: '2026-11-22'
status: done
baseline_revision: '2fc1f69be1412b2a258950502d495691ccd12bf7'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: true
context:
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/module-map.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Story 0.1 lets agent tokens in (sub = Cognito sub, `custom:username`, `act={run, principal}`), but every audit column then records the plain username, so an agent write is indistinguishable from the person's own. Jobs write a bare `system` in EMS (5 places) and PCS (`getCurrentUserIdOrSystem`), against the module map's `system:<job>` rule; only CUMS has `actorOr(job)`.

**Approach:** One shared resolver `ch.batbern.shared.security.Actors` turns the current request into the recorded actor: `agent:<run> for <username>` for a token carrying `act`, the username otherwise, `system:<job>` without a user. Who-acted values are wrapped **at the sink** (where an audit column is written or a who-acted value leaves the service), so ownership and identity code keeps reading the username untouched. The three `SecurityContextHelper`s delegate to it (`getCurrentActor()`, `actorOr(job)`, `carriesAgentAct()`); the STOMP principal of an agent token is named after the actor; audit columns narrower than 255 are widened by new migrations.

## Boundaries & Constraints

**Always:** Format exactly `agent:<run> for <username>` (`run` = `act.run`, `username` = `custom:username`). `Actors.forUsername(v)` is idempotent: null stays null, a value already starting `agent:` or `system:` is returned unchanged, without an agent token it returns `v`. Identity/ownership columns keep the username: `requester_username`, `author_username`, `attendee_username`, `recipient_username`, `proposed_by_username`, `posted_by_username`, `thanked_by_username`, `suggested_by`, `submitted_by_username`, `sender_username`, `assignee_username`, `assigned_organizer_username`, `organizer_username` (events, outreach_history), `speaker_username`, every lookup/ownership comparison (`UserOwnership`, `PartnerSecurityService`, `findBy…Username`, `NotificationController`), and the user-reference merge columns in EMS `UserMergeService` (no audit column is merged). Cognito and Watch behaviour unchanged (STOMP principal stays `sub` for them). TDD; Testcontainers; tests mint real agent tokens with `TestTokens` (runtime keys, no committed private key); new Flyway migrations only, never edit applied ones; contract-first: spec change before code; checkstyle clean; one Gradle module at a time, `--max-workers=4`, logs `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/0-2-<name>.log`.

**Never:** No audience guard (story 7, it will call `Actors.carriesAct`), no grants/mint/settings store (stories 3-6), no change to domain-event `userId` semantics except PCS's `getCurrentUserIdOrSystem` replacement, no change to EMS `SpeakerReminderLog.triggeredBy = "SYSTEM"` (partial index `WHERE triggered_by='SYSTEM'`, existing data) or CUMS `"anonymous"` fallbacks, no change to `JwtRolesConverter`, no git push/PR, no `make test`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Agent write | Jwt with `act={run:R, principal:P}`, `custom:username=U` | audit column = `agent:R for U`; ownership by `U` passes | — |
| Cognito/mock user | Jwt without `act`, or `@WithMockUser` | audit column = username, as today | — |
| No authentication | scheduler / SQS / anonymous | `actorOr(job)` = `system:<job>` | `Actors.system` rejects blank job or one not matching `[a-z0-9-]+` with `IllegalArgumentException` |
| Already resolved | value `system:x` or `agent:R for U` passed down a layer | unchanged (no double wrap) | — |
| `act` present but no `run` | (decoder refuses it; defensive) | `carriesAct` true, `agentAct` empty, `forUsername` returns username | — |
| STOMP CONNECT, agent token | real decoder | principal name `agent:R for U` | invalid → rejected as today |
| STOMP CONNECT, Cognito/Watch | — | principal name = `sub` (unchanged) | — |
| Longest actor | 36-char run + 100-char username = 147 chars | fits every switched column (≥ 255) and the `performedBy` contract field | — |

</intent-contract>

## Code Map

- `shared-kernel/src/main/java/ch/batbern/shared/security/` -- new `Actors` (final, static): `record AgentAct(String run, String principal, String username)`; `Optional<AgentAct> agentAct(Jwt)`; `boolean carriesAct(Authentication)`; `Optional<AgentAct> currentAgentAct()` (SecurityContextHolder, principal `Jwt` or `JwtAuthenticationToken`); `String forUsername(String)`; `String forJwt(Jwt)` (agent → actor, else `custom:username` non-blank else `sub`); `String system(String job)`; constants `AGENT_PREFIX`, `SYSTEM_PREFIX`. Neighbour `BatbernJwtDecoder.AGENT_ISSUER`, `AgentTokenValidator` (claim rules) from 0.1.
- `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/security/TestTokens.java` -- `agentToken(sub, username, run, principal)` exists; reuse.
- Helpers: `services/company-user-management-service/.../companyuser/security/SecurityContextHelper.java:96-102` (`actorOr` → delegate to `Actors.forJwt` / `Actors.system`), `services/event-management-service/.../events/security/SecurityContextHelper.java` (keep Pattern 3b username lookup), `services/partner-coordination-service/.../partners/security/SecurityContextHelper.java:75-80` (`getCurrentUserIdOrSystem` → remove, callers use `actorOr`). Each gets `getCurrentActor()` = `Actors.forUsername(getCurrentUsername())`, `actorOr(job)`, `carriesAgentAct()`.
- CUMS sinks (wrap with `Actors.forUsername`): `service/CompanyService.java:94,186-192` (`companies.created_by`), alias/merge-log/dismissal writers reached from `CompanyService.java:346`, `CompanyMergeController.java:71,98`, `UserMergeController.java:81,142` (`CompanyMergeLog`, `CompanyAliasService`, `CompanyDuplicateCandidateService.java:117`, `DuplicateCandidateService.java:112`), `UserMergeService.merge` entry (`performedBy` sent through the user-reference contract), the company key mover's `performedBy` (company-reference contract), `CompanyLogoRequestService`/`ProfileMergeRequestService` `decide(...)` (`decided_by`). Check each caller passes the current caller (self-claim passes the claimant: correct).
- EMS sinks: `service/EventWorkflowStateMachine.java:128`, `service/TopicService.java:519` (`events.updated_by`); `controller/AdminSettingsController.java:38-40` → `actorOr("admin-settings")`; `controller/TaskTemplateController.java:168-171` → `actorOr("task-templates")`; `service/EventTaskService.java:506`; `service/SpeakerWorkflowService.java:741` (`speaker_status_history.changed_by_username`); `service/slotassignment/SessionTimingService.java:98,145`; `service/QualityReviewService.java:155,175`; `mailcenter/ConversationService.java:79`; `mailcenter/ConversationReplyService.java:121`; `service/EventPhotoUploadHandler.java:106-115` (`system:event-photo-upload`); `service/SessionMaterialUploadHandler.java:204`; `service/SpeakerInvitationService.java:106-107,129` (actor `actorOr("speaker-invitation")` for the transition; outreach `organizer_username` keeps the username, its `"system"` → `system:speaker-invitation`); `service/publishing/PublishingService.java:113-114` → `actorOr("agenda-publishing")`; `security/JwtStompInterceptor.java:56-58` (principal name `Actors.forJwt` when `agentAct` present, else `sub`).
- PCS sinks: `service/PartnerMeetingService.java:92` (`partner_meetings.created_by`); `service/PartnerService.java:375,391` → `actorOr("partner-events")`.
- Contract: `docs/api/internal/cums-internal.openapi.yml:1171-1174,1289-1292` `performedBy` maxLength 100 → 255; implementers' hand-written records `@Size(max = 100) String performedBy` → 255: EMS `usermerge/InternalUserMergeController.java:75`, `usermerge/InternalCompanyReferenceController.java:57`; PCS `usermerge/InternalUserReferenceController.java:56`, `usermerge/InternalCompanyReferenceController.java:64`. Regenerate whatever the spec generates (check build.gradle of CUMS for an internal-spec generator).
- Migrations (next free numbers, confirm with `ls | sort -V`): CUMS `V40__agent_actor_fits_audit_columns.sql` (`profile_merge_requests.decided_by`, `company_logo_requests.decided_by`, `user_duplicate_dismissals.dismissed_by`); EMS `V161__…` (`app_settings.updated_by`, `task_templates.created_by_username`, `event_tasks.completed_by_username`, `speaker_status_history.changed_by_username`, `mail_conversations.updated_by`, `speaker_arrivals.confirmed_by_username`, `sessions.completed_by_username`, `user_merge_log.performed_by`); PCS `V13__…` (`partner_meetings.created_by`, `partner_user_merge_log.performed_by`); all `ALTER COLUMN … TYPE VARCHAR(255)` (metadata-only in PG, no views depend on them). Update matching `@Column(length = 100)` in `ProfileMergeRequest`, `CompanyLogoRequest`, `TaskTemplate`, `SpeakerStatusHistory`, `EventTask`, `SpeakerArrival`, `Session`, `AppSettingEntity`, `PartnerMeeting`.
- Test stubs: EMS IT schema has `user_profiles` (testmigration V100) but no `role_assignments`; PCS has neither. `JwtRolesConverter` resolves an agent token's roles from them by `sub`. Create the missing stub tables in the new ITs (`CREATE TABLE IF NOT EXISTS` via `JdbcTemplate`, or a testmigration whose version collides with no main migration).
- Docs: `docs/architecture/module-map.md` (/internal rule lines 11-16); doc-drift mappings in `.github/doc-drift-mappings.yml` for `shared-kernel/` and the touched paths.

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/src/test/java/ch/batbern/shared/security/ActorsTest.java` -- RED first: every row of the I/O matrix with real `Jwt` objects -- proves the resolver.
- [x] `shared-kernel/src/main/java/ch/batbern/shared/security/Actors.java` -- implement; Javadoc states the convention and that ownership never uses it -- GREEN.
- [x] Three `SecurityContextHelper`s -- add `getCurrentActor()`, `actorOr(job)`, `carriesAgentAct()` delegating to `Actors`; PCS `getCurrentUserIdOrSystem` removed; extend each helper's existing unit test.
- [x] CUMS, EMS, PCS sinks in the Code Map -- wrap who-acted values; bare `"system"` → `system:<job>`; then `grep -rn '"system"' services/*/src/main/java` shows only `BatbernAiService` (chat role) and `SpeakerReminderLog`/`SpeakerReminderService` (`"SYSTEM"`, out of scope).
- [x] `JwtStompInterceptor` + `JwtStompInterceptorTest` -- agent CONNECT principal name `agent:R for U`; Cognito/Watch name unchanged.
- [x] Spec + four implementer records + migrations + `@Column` lengths -- as in Code Map; contract tests (`ContractServer`, `S2sProviderContractIntegrationTest`) stay green.
- [x] `services/company-user-management-service/src/test/java/ch/batbern/companyuser/security/AgentAttributionIntegrationTest.java` -- user row with `cognito_user_id = sub`, role ORGANIZER; agent token as Bearer: `POST /api/v1/companies` → `companies.created_by = agent:R for U` (read through the DB); same token `PATCH /api/v1/users/{U}` (UserOwnership) → 200; positive control: a non-agent `jwt()` organizer create records plain `U`.
- [x] `services/event-management-service/src/test/java/ch/batbern/events/security/AgentAttributionIntegrationTest.java` -- organizer agent token: an organizer write with an audit column (e.g. `PUT /api/v1/admin/settings/{key}` → `app_settings.updated_by`, or a workflow transition → `events.updated_by`) records `agent:R for U`; ownership-guarded write by `U` with the same token (e.g. mark own notification read, `findByIdAndRecipientUsername`) → 200, another user's notification → 404.
- [x] `services/partner-coordination-service/src/test/java/ch/batbern/partners/security/AgentAttributionIntegrationTest.java` -- organizer agent token `POST` partner meeting → `partner_meetings.created_by = agent:R for U`; an ownership-guarded PCS write by `U` (one guarded by `PartnerSecurityService` or another username comparison; CUMS stubbed at the wire as existing PCS ITs do) still succeeds with an agent token.
- [x] `docs/architecture/module-map.md` -- /internal rule: agent-service calls no `/internal/**` except CUMS `/internal/agent-*` (agent-tokens, agent-grants for delegate requests), checked by its caller secret (AD-2, epic decision 2026-11-21); attribution convention: who-acted columns record `Actors` (`agent:<run> for <username>`, `system:<job>`), identity and ownership read the username, STOMP principal of an agent is its actor. Plus mapped docs per doc-drift.

**Acceptance Criteria:**
- Given an agent token for organizer U in run R, when it creates a company (CUMS), changes an admin setting or event (EMS) and creates a partner meeting (PCS), then each audit column reads `agent:R for U`, and an ownership-guarded write by U with the same token succeeds in each service.
- Given a Cognito or mock-user caller, when the same writes run, then the audit columns record the plain username as before (existing suites green without edits to their assertions, except tests that pinned a bare `system`).
- Given no authenticated user, when EMS admin-settings/task-template/publishing/invitation/photo paths or PCS partner events fall back, then they record `system:<job>`; grep shows no bare `"system"` audit value left in EMS/PCS main code.
- Given the module map, when read, then it names the `/internal/agent-*` exception and the attribution convention.

## Implementation Notes

- `Actors.forUsername(v)` names the token's person (`custom:username`), not `v`, under an agent
  token: the actor is who acted. Every sink in the Code Map receives the caller's username, so both
  are the same there.
- EMS and PCS `actorOr(job)` treat any signed-in, non-anonymous caller as a user (mock users
  included); CUMS keeps its JWT-only rule (an `/internal` call carries the user's token or none).
  PCS `actorOr` falls back to `system:<job>` when the principal is of an unsupported type, as
  `getCurrentUserIdOrSystem` did.
- Plan-listed transitions now pass the actor into domain events where the plan put `actorOr` at the
  caller (publishing `EventPublishedEvent`, invitation `SpeakerWorkflowStateChangeEvent`): the
  organizer notification text then names `agent:<run> for <username>`. No event consumer uses that
  value as an identity (checked: `OrganizerNotificationListener` only prints it).
- The three ITs create the missing `role_assignments` (EMS, PCS) and `user_profiles` (PCS) stubs with
  `CREATE TABLE IF NOT EXISTS` in `@BeforeEach`; no class-level `@Transactional`.

## Plan Change Log

- `decidedBy` of the CUMS Mail Center request contract (`MailCenterRequestDecided`, EMS
  `InternalRequestConversationController`) widened 100 → 255 as well: `decide(...)` sends the wrapped
  decider to EMS, which a 100-character limit would have refused (same class as `performedBy`).
- `companies-api` `CompanyResponse.createdBy` pattern extended by `agent:\S+ for <username|sub>`:
  the response conformance gate would otherwise have failed every agent-created company. Descriptions
  of `partner-meetings-api` `createdBy` and `events-core-api` `updatedBy` name the actor; frontend
  types regenerated for the three specs (descriptions only).
- `Actors.usernameOf(actor)` added, and EMS `RegistrationService.cancelForCancelledEvent` uses it:
  it read `events.updated_by` as the cancellation mail's sender (`mail_message.sender_username`,
  an identity column of VARCHAR(100)), so an agent cancellation would have recorded the actor as
  sender. Proven RED first by the EMS IT.
- EMS `SessionMaterialsService.uploadMaterialFromUrl` (batch import) wraps `uploaded_by` like its
  sibling `SessionMaterialUploadHandler`.
- Not wrapped, on purpose: newsletter and slides-online `sent_by_username` (VARCHAR(100)): audience
  mails are organizer-sent only (#1179); whether an agent may send them is a later story's decision.

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 5 findings — high 0, medium 2, low 3, false 0, maybe-false 0
- findings:
  - `[medium]` `[patch]` PCS `UserReferenceService` merges `partner_meetings.created_by` by exact username, so an agent-created meeting (`agent:R for <source>`) was neither counted nor moved — fixed: preview and merge also match the exact `agent:` prefix + ` for <source>` suffix (no LIKE built from the username) and rewrite it to the target; test `merge_movesAgentActorOfTheSource` incl. a lookalike username left untouched.
  - `[medium]` `[patch]` REST watch paths (`LiveTimingController`, `WatchEventController.confirmArrival`) pass the JWT sub, so the widened `sessions.completed_by_username` / `speaker_arrivals.confirmed_by_username` recorded the Cognito sub under an agent token — fixed: `WatchSessionService.endSession` and `WatchSpeakerArrivalService.confirmArrival` wrap with `Actors.forUsername` (idempotent for STOMP); agent cases added to both watch ITs.
  - `[low]` `[patch]` `SpeakerInvitationService` called `getCurrentUsername()` (throws without auth) before the `system:speaker-invitation` fallback, making it unreachable — fixed: `getCurrentUsernameOrNull()`.
  - `[low]` `[patch]` EMS `SecurityContextHelper.actorOr` Javadoc claimed "Never throws" though an unsupported principal type throws — fixed: claim removed.
  - `[low]` `[patch]` new CUMS test code used fully qualified `java.util.Map/List` (owner rule: imports) — fixed: imports added.

## Design Notes

Architecture note: modules touched -- shared-kernel security (L0), CUMS/EMS/PCS platform security + the audit sinks named above, EMS STOMP. New dependencies -- none (services already use shared-kernel). Endpoints -- none new; `performedBy` of the CUMS user-/company-reference contracts widened to 255 (nearest: same operations; must be extended, the actor does not fit 100). Reused -- `TestTokens`, `BatbernJwtDecoder`, `JwtRolesConverter` (agent roles from `role_assignments` by sub), CUMS `actorOr` (generalised). Deviations -- none; the module map gains the AD-2 exception the epic orders.

Wrapping at the sink rather than at the controller keeps every caller that passes the username for ownership or lookup untouched; idempotence makes double wrapping (controller `actorOr` → state machine sink) harmless. Detection is by the `act` claim (what the epic says the audience guard checks), not by issuer.

```java
event.setUpdatedBy(Actors.forUsername(organizerUsername)); // sink
String updatedBy = securityContextHelper.actorOr("admin-settings"); // no-user fallback
```

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test :shared-kernel:publishToMavenLocal --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-2-shared-kernel.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-2-cums.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:partner-coordination-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-2-pcs.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-2-ems.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :api-gateway:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-2-gateway.log` -- expected: BUILD SUCCESSFUL (shared-kernel change)
- `set -o pipefail; ./gradlew checkstyleMain checkstyleTest --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-2-checkstyle.log` -- expected: BUILD SUCCESSFUL
- `make check-test-ratchet` -- expected: no RAISED line

## Auto Run Result

**Summary:** Shared resolver `ch.batbern.shared.security.Actors` records who acted: `agent:<run> for <username>` for a token carrying `act`, the username otherwise, `system:<job>` without a user. Wrapped at the audit sinks in CUMS, EMS and PCS, so ownership and identity code keeps reading the username. The three `SecurityContextHelper`s gained `getCurrentActor()`, `actorOr(job)`, `carriesAgentAct()`; PCS `getCurrentUserIdOrSystem` and every bare `"system"` audit value are gone. EMS STOMP names an agent principal after its actor. Module map names the `/internal/agent-*` exception and the attribution convention.

**Files:** shared-kernel `Actors` + `ActorsTest`; CUMS/EMS/PCS helpers and sinks; migrations CUMS V40, EMS V161, PCS V13 (audit columns to VARCHAR(255)) with `@Column` lengths; `cums-internal` `performedBy`/`decidedBy` maxLength 255 and the implementer records; `companies-api` `createdBy` pattern, two spec descriptions, regenerated frontend types; three `AgentAttributionIntegrationTest`s, STOMP and watch test cases, PCS merge test; docs `module-map.md`, `06-backend-architecture.md`, doc-drift mapping.

**Review:** quick lens, 5 findings: 5 patched (2 medium, 3 low), 0 deferred, 0 rejected.

**Follow-up review recommended:** true. Two medium entries were patched; the unverified risk is the PCS merge suffix rewrite (`UserReferenceService`), new SQL reviewed only by its own test, not by a second reviewer.

**Verification (after the patches):** shared-kernel 776/0 failures (full, --rerun-tasks); CUMS 1090/0 (15 skipped); PCS 318/0; EMS 3151/0 (6 skipped); checkstyle main+test clean; `make check-test-ratchet` no RAISED. Gateway 333/0 (26 skipped) before the patches (patches touched no gateway or shared-kernel code).

**Residual risks:** no run against local dev or staging; domain-event `userId` keeps the username except where `actorOr` sits at the caller (publishing, invitation), so organizer notification texts are mixed; newsletter/slides-online `sent_by_username` not wrapped (audience mails, later decision); EMS `"SYSTEM"` reminder trigger and CUMS `"anonymous"` unchanged.
