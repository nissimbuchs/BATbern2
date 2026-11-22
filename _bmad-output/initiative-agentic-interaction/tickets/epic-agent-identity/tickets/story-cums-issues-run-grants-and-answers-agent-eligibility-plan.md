---
title: 'CUMS issues run grants and answers agent eligibility'
type: 'feature'
ticket: '5'
created: '2026-11-22'
status: done
baseline_revision: '9ae07fa9e0cbdd04db2fe56ee6ff84bbc27c7300'
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

**Problem:** Story 6 may mint an agent token only against a CUMS run grant, and EMS must ask CUMS whether a sender is agent-eligible (R4, R5). Neither operation exists, and CUMS `/internal/**` is `permitAll` in the filter chain, so nothing authenticates who asks for a grant.

**Approach:** Contract-first in `docs/api/internal/cums-internal.openapi.yml`: `POST /internal/agent-grants` and `GET /internal/agent-eligibility`. Both check a caller secret themselves (header `X-Agent-Caller-Secret`, constant-time). A grant is a row of the new `agent_grants` (V42): one run, one principal, at most 1 h, opaque secret returned once and stored as a SHA-256 hash. `AgentGrantService.findActiveGrant(runId, grantSecret)` is the seam story 6 mints against; no token is minted here.

## Boundaries & Constraints

**Always:**
- Triggers, caller and principal (trigger decides the principal, never the body):

| `trigger` | Caller secret | Extra proof | Principal kind / username |
|---|---|---|---|
| `EMS_HANDOFF` | EMS | body `username` | `REQUESTER` / that username |
| `ORGANIZER_LOGIN` | agent-service | forwarded Cognito JWT (no `act`, `iss` not `batbern-watch`/`batbern-agent`) | `ORGANIZER` / the JWT's username (`custom:username`, else `sub`), body carries none |
| `LINKED_RUN` | agent-service | body `linkedRunId` + `linkedRunGrantSecret`: an active `REQUESTER` grant (`findActiveGrant`) | `ORGANIZER_DELEGATE` / `AgentSettingsService.delegateUsername()` |
| `SCHEDULER_JOB` | agent-service | body `jobName` listed in `agent.grants.scheduler-jobs` (comma-separated, default empty) | `ORGANIZER_DELEGATE` / the delegate |

- Check order, first failure wins: caller secret (401 `CALLER_SECRET_INVALID`) → body shape (400) → organizer login (401 `ORGANIZER_LOGIN_REQUIRED`) → switch (403 `AGENT_SWITCHED_OFF`) → linked run / job (403 `LINKED_RUN_INVALID` / `SCHEDULER_JOB_UNKNOWN`) → principal (403 `PRINCIPAL_NOT_ELIGIBLE`: no delegate, or `AgentSettingsService.isAllowlisted(username)` false, which also covers unknown users and lost ORGANIZER). Every principal, the delegate included, must be allowlisted (R5). Error body = shared `ErrorResponse` with `details.code`.
- Eligibility: `GET /internal/agent-eligibility?username=`, EMS caller secret only; 200 `{username, eligible: true}` only when switch on and `isAllowlisted`; otherwise 403 `AGENT_SWITCHED_OFF` / `PRINCIPAL_NOT_ELIGIBLE` (owner declined 200-false, R5); 401 `CALLER_SECRET_INVALID`; 400 without `username`.
- Caller secrets: properties `agent.callers.ems.secret` and `agent.callers.agent-service.secret`, mapped in CUMS `application.yml` to `${AGENT_CALLERS_EMS_SECRET:}` / `${AGENT_CALLERS_AGENT_SERVICE_SECRET:}` (relaxed binding cannot map the second, story 3 left the names here). Blank configured secret refuses everything. Compare SHA-256 digests with `MessageDigest.isEqual`. Never log a secret.
- Grant row: `run_id` (random UUID string, 36 chars, fits the 147-char actor of story 2), `principal_kind`, `username` (FK `user_profiles(username)` ON DELETE CASCADE ON UPDATE CASCADE), `trigger_kind`, `linked_run_id`, `job_name`, `secret_hash` (hex SHA-256 of 32 random bytes, base64url-encoded secret), `requested_by` (Actors: `system:ems-agent-handoff`, `system:agent-service`, or the organizer's username), `issued_at`, `expires_at` = issued + 1 h; DB CHECKs: enums lowercase, `expires_at > issued_at AND expires_at <= issued_at + interval '1 hour'`, linked-run / job columns set exactly for their trigger. Response 201 carries `grantSecret` once; it is never readable again.
- TDD, Testcontainers (`AbstractIntegrationTest`, `@Import(TestAwsConfig.class)`), production chain, real tokens (`TestTokens.agentToken`, a Watch token from CUMS `WatchJwtService`), Cognito-shaped JWT via `jwt()` with the real `JwtAuthenticationConverter`; production-shaped names; no class-level `@Transactional`, cleanup in `@AfterEach` (restore `agent_settings` as V41 leaves it, empty `agent_allowlist`, `agent_grants`). Test secrets in `application-test.yml`, values prefixed `test-only-`. Checkstyle clean.
- rack rules: one Gradle module at a time, `--max-workers=4`; logs via `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/0-5-<name>.log`, grep the log, never re-run a suite to grep. No `make test`.

**Never:** No token minting, no signing key use (story 6). No caller code in EMS or agent-service (later epics). No change to the filter chain (`/internal/**` stays `permitAll`; the check lives in the operations). No migration seeding usernames (C3). No edit of V1-V41. No Bruno file (production post-deploy run). No git push, no PR, no AWS call; do not add the untracked `film-batbern61/*.mp4`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| EMS handoff | switch on, user allowlisted organizer, EMS secret | 201, `REQUESTER`, expires ≤ 1 h after issue, row hash ≠ secret, `findActiveGrant(runId, secret)` present | — |
| Organizer login | agent-service secret + organizer's Cognito JWT, allowlisted | 201, `ORGANIZER`, username from JWT | — |
| Linked run | agent-service secret, active requester grant + its secret, delegate allowlisted | 201, `ORGANIZER_DELEGATE`, delegate username, `linkedRunId` set | — |
| Scheduler job | agent-service secret, listed job, delegate allowlisted | 201, `ORGANIZER_DELEGATE`, `jobName` set | — |
| Switch off | each trigger otherwise valid | 403 `AGENT_SWITCHED_OFF`, no row | — |
| Not listed | user not allowlisted / not organizer any more / unknown / no delegate | 403 `PRINCIPAL_NOT_ELIGIBLE`, no row | — |
| Caller secret | missing, wrong, or the other caller's secret, per trigger | 401 `CALLER_SECRET_INVALID`, no row | — |
| Organizer JWT | absent, or an agent token, or a Watch token | 401 `ORGANIZER_LOGIN_REQUIRED`, no row | — |
| Bad link / job | unknown, expired or non-requester linked run, wrong linked secret; unlisted job | 403 `LINKED_RUN_INVALID` / `SCHEDULER_JOB_UNKNOWN` | — |
| Bad body | unknown trigger, missing field for the trigger | 400 | — |
| Eligibility | listed user / not listed / switch off / bad secret | 200 `eligible: true` / 403 / 403 / 401 | — |
| Seam | unknown run, wrong secret, expired row | `findActiveGrant` empty | — |

</intent-contract>

## Code Map

- `docs/api/internal/cums-internal.openapi.yml` -- add both paths after `/internal/companies/{name}` (style: 1002-1111: description names actor, consumers, examples; errors `$ref: '../_shared.openapi.yml#/components/schemas/ErrorResponse'` with examples incl. `details.code`), schemas `AgentGrantRequest`, `AgentGrant`, `AgentEligibility`; header parameter `X-Agent-Caller-Secret` (required). Info description: add a paragraph that `/internal/agent-*` are the only operations that authenticate their caller. Usernames in examples invented (`anna.meier`).
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/contract/S2sProviderContractIntegrationTest.java:113-131` -- add both operations to the examples `@CsvSource`.
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/InternalUserController.java` -- internal controller style (hand-written mappings, no generated interface, no `@PreAuthorize`; `EndpointAuthorizationGuard` excludes `/internal/`). New `InternalAgentController`; DTOs as records in `.../dto/` (like `TestFixtureCleanupRequest`).
- `.../service/AgentSettingsService.java` -- `isEnabled()`, `isAllowlisted(username)`, `delegateUsername()`; reuse, do not change.
- `.../security/SecurityContextHelper.java:56-118` -- `getCurrentUsername()`; `Actors.carriesAct`, `BatbernJwtDecoder.WATCH_ISSUER` / `AGENT_ISSUER` (shared-kernel) for the Cognito-login check.
- `.../exception/GlobalExceptionHandler.java` -- add one handler for a new `AgentRequestRefusedException(HttpStatus, code, message)` (model: `ReservedEmailRecipientException` handler ~214, `details.code`).
- `.../domain/AgentAllowlistEntry.java`, `.../repository/AgentAllowlistRepository.java` -- entity / repository style for `AgentGrant` + `AgentGrantRepository`. Enums stored lowercase via `AttributeConverter`.
- `services/company-user-management-service/src/main/resources/db/migration/` -- last is `V41__agent_settings.sql`; add `V42__agent_grants.sql`.
- `services/company-user-management-service/src/main/resources/application.yml` -- no `agent:` block yet; add callers + `agent.grants.scheduler-jobs: ""`.
- `.../src/test/resources/application-test.yml` -- test caller secrets, `agent.grants.scheduler-jobs: test-digest`.
- `.../test/.../controller/AgentSettingsControllerIntegrationTest.java:56-130` -- seeding users with sub + role, `cognito(...)` post-processor, singleton restore SQL; copy the pattern.
- `.../watch/WatchJwtService.java:82` -- `generateTokenWithExpiry(username)` gives a real Watch token.
- Docs (doc-drift maps CUMS to 06): `docs/architecture/06-backend-architecture.md:132-135` (caller-secret properties sentence, then a new "Agent grants" paragraph after "Agent settings"), `docs/operations/runbooks.md:362-363` (property column), `docs/architecture/module-map.md:23-26` (agent-* check their caller secret; EMS calls `/internal/agent-grants` and `/internal/agent-eligibility`).

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/internal/cums-internal.openapi.yml` -- spec first, as in Code Map; 201/400/401/403 for grants, 200/400/401/403 for eligibility, statuses and codes documented; `npm run lint:api-internal` clean.
- [x] `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/InternalAgentGrantIntegrationTest.java` -- RED first: one test per matrix row (each refusal path its own test, per trigger where the row says so), responses checked with `ProviderContract.assertResponseMatchesSpec` for non-2xx too; absence assertions (no row) with a positive control (same request with the fix gets 201).
- [x] `services/company-user-management-service/src/main/resources/db/migration/V42__agent_grants.sql` -- as in Boundaries.
- [x] `.../domain/AgentGrant.java` (+ enums `AgentPrincipalKind`, `AgentGrantTrigger` with converters), `.../repository/AgentGrantRepository.java`.
- [x] `.../security/AgentCallerSecrets.java` -- `enum Caller {EMS, AGENT_SERVICE}`, `boolean matches(Caller, String presented)`; logs once at startup which callers have no secret. Unit test `AgentCallerSecretsTest` (blank config, wrong, right, other caller's).
- [x] `.../service/AgentGrantService.java` -- `issue(request, presentedCallerSecret)`, `confirmEligible(username, presentedCallerSecret)`, `findActiveGrant(runId, grantSecret)` (seam, Javadoc names story 6 and that minting re-checks switch and allowlist); `@Transactional`.
- [x] `.../controller/InternalAgentController.java`, `.../dto/AgentGrantRequest.java`, `AgentGrantResponse.java`, `AgentEligibilityResponse.java`, `.../exception/AgentRequestRefusedException.java` + handler.
- [x] `application.yml`, `application-test.yml` -- properties.
- [x] `S2sProviderContractIntegrationTest` -- examples list.
- [x] Docs as in Code Map; runbook property column filled, "story 5" placeholders removed.

**Acceptance Criteria:**
- Given the switch on and an allowlisted organizer, when EMS, an organizer login, a linked run and a listed job each request a grant with the right caller secret, then each gets 201 with the principal of the table and the stored row holds only the secret's hash.
- Given any one refusal condition of the matrix, when the grant is requested, then the status and `details.code` of the matrix come back and no `agent_grants` row exists.
- Given EMS's secret, when eligibility is asked for a listed user, then 200 `eligible: true`; for anyone else or with the switch off 403, with a bad secret 401.
- Given an issued grant, when `findActiveGrant` is called with its run id and secret, then it is found; with a wrong secret, an unknown run or an expired row, then it is empty.

## Implementation Notes

- Caller check: the presented secret must match one caller before the body is read; once the trigger is parsed it must match that trigger's caller (401 otherwise). `AgentGrantRequest` keeps `trigger` a string and collects unknown fields (`@JsonAnySetter`), so a wrong body from an unknown caller answers 401, not 400. Exception: malformed JSON fails in Jackson before the controller (400, `ClientErrorClassifier`).
- A field of another trigger (e.g. `username` on `ORGANIZER_LOGIN`) and an unknown field are 400.
- `requested_by` of `ORGANIZER_LOGIN` is `Actors.forUsername(username)`; act-carrying tokens are refused before, so it is the plain username.
- V42 adds a CHECK tying principal kind to trigger, and a hex-format CHECK on `secret_hash` (VARCHAR(64), not CHAR, for `ddl-auto: validate`). `linked_run_id` has no FK (a self-FK adds nothing while no grant is ever deleted).
- RED was not witnessed separately: tests and production code were written in one pass. Instead two mutants (delegate skips the allowlist; trigger/caller mismatch accepted) were run against the IT and killed by 6 tests, then reverted.

## Plan Change Log

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 8 findings — high 0, medium 2, low 6, false 0, maybe-false 0
- findings:
  - `[medium]` `[patch]` An undecodable `Authorization: Bearer` (expired, malformed, bad signature) is refused by the resource-server filter with a bare 401 before the caller secret is checked; the spec and 06 claimed caller secret first and a `details.code` on every 401 — documented the exception in the spec (info, both 401s) and 06; new IT `should_refuseWithFilter401_when_bearerUndecodable` (real expired agent token: 401, no row; same request without bearer: 201). Filter chain unchanged (plan Never).
  - `[medium]` `[patch]` Nine refusal tests asserted "no row" without a positive control (coding-standards Assertion rules) — each now also sends the corrected request and expects 201.
  - `[low]` `[patch]` Matrix "Not listed" lacked delegate-not-listed for `LINKED_RUN` and lost-ORGANIZER for `ORGANIZER_LOGIN` — two tests added, with positive controls.
  - `[low]` `[reject]` RED not witnessed (tests and code in one pass) — cannot be undone after the fact; disclosed in Implementation Notes, and two mutants (delegate skips allowlist, caller/trigger mismatch accepted) were killed by 6 tests.
  - `[low]` `[patch]` Eligibility accepted a `username` over the spec's 100 characters (403 instead of 400) — 400 like the grant path, tested with 101 characters.
  - `[low]` `[patch]` Eligibility's 400 carried the grant code `INVALID_AGENT_GRANT_REQUEST`, undocumented — one code `INVALID_AGENT_REQUEST` for both operations, named in both spec 400s and 06.
  - `[low]` `[reject]` A field of another trigger sent as explicit JSON `null` passes as absent — nothing is stored or acted on from it, the spec schema already rejects it at the caller's `ContractServer`, and telling null from absent needs presence tracking in the DTO (added complexity) for a body no caller sends.
  - `[low]` `[patch]` module-map bullet left one over-long line — rewrapped.
- orchestrator, same pass: spec named "epic agent runtime" as the EMS handoff's epic; corrected to epic speaker mail agent.

## Design Notes

Architecture note: modules touched -- CUMS (controller, service, security, domain, repository, exception, migration V42, config), cums-internal spec, docs. New dependencies -- none (CUMS reads its own `AgentSettingsService`). New endpoints -- `POST /internal/agent-grants`, `GET /internal/agent-eligibility`; nearest existing: `/internal/users/{username}` (a user read, cannot carry a caller-secret check or a grant) and `/api/v1/agent-settings` (public, organizer-only, for people not services). Reused -- `AgentSettingsService` reads, `Actors`, shared `ErrorResponse`, `ProviderContract`, `TestTokens`. Deviation -- the first `/internal` operations that authenticate their caller (module map: `/internal` authorizes by operation); epic decision 2026-11-21, recorded in module-map.md.

Why 401 vs 403: 401 = a credential this trigger needs is missing or invalid (caller secret, organizer login); 403 = the caller is known but policy refuses. The caller secret is checked before the switch so an unauthenticated caller learns nothing about the settings.

Why the linked run needs the parent's grant secret: agent-service holds it (EMS hands it over with the mail), and it proves the link instead of trusting a run id, reusing the seam.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-5-cums.log` -- expected: BUILD SUCCESSFUL (full run: response conformance stale check)
- `set -o pipefail; ./gradlew :services:company-user-management-service:checkstyleMain :services:company-user-management-service:checkstyleTest --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-5-checkstyle.log` -- expected: BUILD SUCCESSFUL
- `cd web-frontend && npm run lint:api-internal` -- expected: no error
- `make check-test-ratchet` -- expected: no RAISED line

## Auto Run Result

**Summary:** CUMS issues agent run grants (`POST /internal/agent-grants`) and answers agent eligibility (`GET /internal/agent-eligibility`), contract-first in `cums-internal.openapi.yml`. Both check the `X-Agent-Caller-Secret` header themselves (SHA-256 digests, `MessageDigest.isEqual`; blank config refuses), the only `/internal` operations that authenticate their caller. Four triggers: `EMS_HANDOFF` (EMS secret, principal `REQUESTER`), `ORGANIZER_LOGIN` (agent-service secret + forwarded Cognito JWT, `ORGANIZER`), `LINKED_RUN` (agent-service secret + an active requester grant's run id and secret, `ORGANIZER_DELEGATE`), `SCHEDULER_JOB` (agent-service secret + a job listed in `agent.grants.scheduler-jobs`, empty by default, `ORGANIZER_DELEGATE`). Refusals: 401 caller secret / organizer login, 403 switch off / link / job / principal not allowlisted (delegate included), 400 body, each with `details.code`. Grants are V42 `agent_grants` rows, one hour, secret returned once and stored hashed. `AgentGrantService.findActiveGrant(runId, secret)` is story 6's minting seam; no token is minted.

**Files:** spec `docs/api/internal/cums-internal.openapi.yml`; CUMS `V42__agent_grants.sql`, `AgentGrant` + `AgentGrantTrigger` + `AgentPrincipalKind`, `AgentGrantRepository`, `AgentCallerSecrets`, `AgentGrantService`, `InternalAgentController`, three DTO records, `AgentRequestRefusedException` + `GlobalExceptionHandler` handler, `application.yml` (caller-secret mapping, scheduler jobs), `application-test.yml`; tests `InternalAgentGrantIntegrationTest`, `AgentCallerSecretsTest`, `S2sProviderContractIntegrationTest` (examples list); docs 06, module-map, runbook.

**Review:** quick lens, 8 findings: 6 patched (2 medium, 4 low), 0 deferred, 2 rejected (low: RED not witnessed, cannot be redone, mutants killed instead; low: explicit-null field of another trigger, harmless and caught by the schema at the caller).

**Follow-up review recommended:** true by the rule (two medium entries patched on a first pass). Named risk: the bare filter 401 for an undecodable bearer is now documented and tested, but how a future caller (EMS handoff, agent-service) handles that body-less 401 is unverified until those callers exist.

**Verification (orchestrator, after patches):** CUMS full test + checkstyle BUILD SUCCESSFUL (`~/.cache/batbern-test-logs/0-5-verify-2.log`): 1170 tests, 0 failures, 0 errors, 15 skipped (JUnit XML); `InternalAgentGrantIntegrationTest` 63 cases; response conformance 577 responses, baseline empty. `npm run lint:api-internal` clean; `make check-test-ratchet` no RAISED/STALE line.

**Residual risks:** no caller exists yet (later epics), so the contract is proven only on the provider side. A missing caller-secret env in AWS refuses that caller with only a startup warning. Expired grant rows are never cleaned up (tiny, at most one per run).
