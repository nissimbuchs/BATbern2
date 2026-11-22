---
title: 'The owner switches the agent and keeps the allowlist in CUMS'
type: 'feature'
ticket: '4'
created: '2026-11-22'
status: 'built'
baseline_revision: '98a4c42129329ee67ef33cf33741ce5756c0f187'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/module-map.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Stories 5-6 must refuse every grant, token and eligibility request unless the agent is switched on and the principal is allowlisted, and must know the organizer delegate. No store or API for those three settings exists, and EMS `app_settings` cannot serve: CUMS issues grants and tokens and cannot call EMS (epic decision 2026-11-21).

**Approach:** A CUMS store (new migration V41: singleton row `agent_settings` + `agent_allowlist`) behind `AgentSettingsService`, exposed contract-first as `GET` / `PATCH /api/v1/agent-settings` in `users-api.openapi.yml`, organizer-only and refusing any token carrying `act` (403), routed to CUMS by the Spring gateway. No UI. The service also offers the read methods stories 5-6 call.

## Boundaries & Constraints

**Always:** Switch off by default: the migration inserts the singleton with `enabled = false`, and a missing row reads as off, empty allowlist, no delegate. `@PreAuthorize("hasRole('ORGANIZER') and !@securityContextHelper.carriesAgentAct()")` on both operations (organizers are the owners, decision 2026-11-21; a delegate run must not change or read its own switch). PATCH is partial (ADR-013): absent field = unchanged; `allowlist` present = the whole list replaced (duplicates collapsed); `delegateUsername: ""` clears the delegate (precedent: `quietHoursStart: ""` in `updateUserSettings`). Every allowlisted username and the delegate must be an existing CUMS user holding ORGANIZER (C3), else 400 naming the offending usernames, nothing stored. `updated_by` / `added_by` record `securityContextHelper.getCurrentActor()`. FK by username to `user_profiles(username)` (allowlist `ON DELETE CASCADE`, delegate `ON DELETE SET NULL`, both `ON UPDATE CASCADE`), as `watch_pairings` does (V39). Response conforms to the spec (response conformance gate). TDD, Testcontainers, real production chain, real agent tokens from `TestTokens`, no class-level `@Transactional`, cleanup in `@AfterEach`. Checkstyle clean; one Gradle module at a time, `--max-workers=4`, logs `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/0-4-<name>.log`.

**Never:** No migration seeds any username (C3, PII). No UI, no frontend service call. No grants, eligibility endpoint or token minting (stories 5-6). No change to EMS `app_settings` / `AdminSettingsController`. No Bruno request that writes (post-deploy Bruno hits production); this story adds no Bruno file at all (a GET would print allowlist usernames into CI logs for little value). No edit of applied migrations (V1-V40). No git push, no PR, no `make test`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fresh DB | migrations only | `GET` 200 `{enabled:false, allowlist:[], delegateUsername:null}` | — |
| Organizer switches on | `PATCH {enabled:true}` (Cognito-style JWT, ORGANIZER) | 200, `enabled:true`, allowlist/delegate unchanged; row `updated_by` = username | — |
| Organizer sets allowlist | `PATCH {allowlist:[a,b,a]}`, a and b organizers | 200, allowlist `[a,b]` sorted; a second PATCH `{allowlist:[b]}` leaves `[b]` | — |
| Organizer sets / clears delegate | `{delegateUsername:"a"}` then `{delegateUsername:""}` | `a`, then `null` | — |
| Non-organizer or unknown username | allowlist or delegate names an attendee or a missing user | 400, message names them; nothing changed (check by GET) | `UserValidationException` |
| Speaker / attendee | any role but ORGANIZER | 403 on GET and PATCH | — |
| Agent token with organizer rights | `TestTokens.agentToken(sub, organizer, run, principal)`, sub has ORGANIZER in `role_assignments` | 403 on GET and PATCH; switch unchanged | — |
| Anonymous | no token | 401 | — |
| Profile deleted | allowlisted / delegate user deleted | drops out of allowlist / delegate becomes null | FK cascade |

</intent-contract>

## Code Map

- `services/event-management-service/.../controller/AdminSettingsController.java`, `service/AdminSettingsService.java` -- reference pattern (generated `*Api`, `@PreAuthorize`, actor as `updated_by`); do not change.
- `services/company-user-management-service/src/main/resources/db/migration/` -- last is `V40__agent_actor_fits_audit_columns.sql`; add `V41__agent_settings.sql`.
- `services/company-user-management-service/.../companyuser/security/SecurityContextHelper.java` -- `@Component` bean `securityContextHelper`: `carriesAgentAct()` (story 0.2, via `Actors.carriesAct`), `getCurrentActor()`.
- `.../companyuser/controller/UserSettingsController.java` -- CUMS controller style (implements generated `UserSettingsApi`, `@RequestMapping("/api/v1")`, `@Timed`).
- `.../companyuser/exception/UserValidationException.java` + `GlobalExceptionHandler` -- 400 with `details`; `AuthorizationDeniedException` handler gives 403.
- `.../companyuser/repository/UserRepository.java` -- `findByUsername`; `domain/User` has `roles` (`ch.batbern.shared.types.Role`).
- `services/company-user-management-service/build.gradle` -- `openApiGenerateUsers` generates `users-api` into `ch.batbern.companyuser.api.generated` / `dto.generated` (tags → interface names); users-api already in `responseConformanceSpecs`.
- `docs/api/users-api.openapi.yml` -- add tag `Agent Settings`, path `/agent-settings` (get `getAgentSettings`, patch `updateAgentSettings`), schemas `AgentSettings`, `UpdateAgentSettingsRequest`; reuse `#/components/responses/{BadRequest,Unauthorized,Forbidden}`; model after `/users/me/settings` (2023-2120). Example usernames invented (`anna.muster`).
- `api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java:123-126` -- add `/api/v1/agent-settings` to the CUMS branch; test in `api-gateway/src/test/java/ch/batbern/gateway/routing/DomainRouterTest.java`.
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/security/AgentAttributionIntegrationTest.java` -- model for seeding users with sub + role and sending real agent tokens (`TestTokens.agentToken`) and Cognito-shaped `jwt()` with the real `JwtAuthenticationConverter`.
- `web-frontend/package.json` `generate:api-types:users` -- regenerates committed `src/types/generated/user-api.types.ts`.
- Docs: `docs/architecture/module-map.md:121` (reuse row), `docs/architecture/06-backend-architecture.md` (agentic section near 114-150; mapped for CUMS and api-gateway in `.github/doc-drift-mappings.yml`).

## Tasks & Acceptance

**Execution:**
- [x] `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/AgentSettingsControllerIntegrationTest.java` -- RED first: every matrix row through MockMvc, extends `AbstractIntegrationTest`, `@Import(TestAwsConfig.class)`; users with production shapes (e.g. `Hông Ân`); restore the singleton (`enabled=false`, delegate null, allowlist empty) in `@AfterEach`; fresh-DB test reads the migrated row before any write; absence (403 leaves switch off) with a positive control (same body by Cognito organizer flips it).
- [x] `docs/api/users-api.openapi.yml` -- spec first, as in Code Map; `allowlist` array of username strings (`maxItems: 100`, items `maxLength: 100`), `delegateUsername` nullable in the response, `updatedAt` date-time nullable, `updatedBy` nullable; descriptions state off by default, organizer-only, agent tokens refused, empty string clears delegate, 400 rules.
- [x] `services/company-user-management-service/src/main/resources/db/migration/V41__agent_settings.sql` -- tables, FKs, `CHECK (id = 1)` singleton, insert `(1, false)` only.
- [x] `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/AgentSettingsState.java`, `AgentAllowlistEntry.java`, `repository/AgentSettingsStateRepository.java`, `AgentAllowlistRepository.java` -- JPA mapping.
- [x] `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/AgentSettingsService.java` -- `get()`, `update(request, actor)` (validation, all-or-nothing, `@Transactional`), plus `isEnabled()`, `isAllowlisted(String)`, `delegateUsername()` (`Optional<String>`) for stories 5-6; Javadoc names the module-map exception.
- [x] `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/AgentSettingsController.java` -- implements generated `AgentSettingsApi`, the `@PreAuthorize` above on both methods.
- [x] `api-gateway/.../routing/DomainRouter.java` + `DomainRouterTest.java` -- route + test.
- [x] `web-frontend/src/types/generated/user-api.types.ts` -- regenerate.
- [x] `docs/architecture/module-map.md`, `docs/architecture/06-backend-architecture.md` -- reuse-table exception row (CUMS `AgentSettingsService`, `/agent-settings`, beside EMS `app_settings`, reason: CUMS cannot call EMS, decision 2026-11-21); short "Agent settings" paragraph.

**Acceptance Criteria:**
- Given a fresh database, when an organizer calls `GET /api/v1/agent-settings`, then the switch reads off with an empty allowlist and no delegate.
- Given an organizer's login, when they change the switch, the allowlist and the delegate one at a time, then each change is returned and read back by GET, the others unchanged.
- Given a speaker's login, an agent token for an organizer, or no login, when either operation is called, then the answer is 403, 403, 401 and the stored switch is unchanged.
- Given the gateway, when `/api/v1/agent-settings` is routed, then it goes to company-user-management-service.

## Implementation Notes

- Test usernames follow `chk_username_format` (`^[a-z]+\.[a-z]+(\.[0-9]+)?$`, V4), so they are `hongan.agentset` etc.; the production shapes are in the names (`Hông Ân`, `O’Neill`, `Brünnimann`, `王`).
- `UserRepository.findUsernamesWithRole(usernames, role)` added for the organizer check (one query).
- `PATCH` takes a pessimistic lock on the singleton row, so two concurrent allowlist replacements run one after the other; an allowlist entry that stays keeps its `added_at` / `added_by`.
- A missing row is created by the first `PATCH` (test `should_readOff_when_rowMissing`).
- 400 body: `message` names the offending usernames, `details.usernames` lists them.
- The anonymize snapshot script picks up both FKs by column name (`username`, `delegate_username`); no change needed.

## Plan Change Log

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 4 findings — high 0, medium 1, low 3, false 0, maybe-false 0
- findings:
  - `[medium]` `[patch]` `isAllowlisted` / `delegateUsername()` read only the stored rows, so an organizer whose role is removed later (`RoleService` touches only `role_assignments`) stays allowlisted / delegate and stories 5-6 would grant for them (C3); the check-before-lock race in `update()` is the same gap — fixed: both reads also require ORGANIZER at call time (`holdsOrganizer`), Javadoc and `06-backend-architecture.md` updated, new IT `should_notCountAllowlistOrDelegate_when_organizerRoleRemoved` with positive control.
  - `[low]` `[reject]` concurrent first `PATCH` with the singleton row missing races on the insert of `id = 1` — V41 inserts the row; it is missing only after a manual delete, and a fix (upsert/retry) adds code for a state the API never produces.
  - `[low]` `[patch]` the 400 test did not assert `details.usernames` — fixed: jsonPath asserts exactly the two offending usernames.
  - `[low]` `[patch]` the delegate test did not assert `enabled` unchanged — fixed: asserted after set and after clear.

## Design Notes

Architecture note: modules touched -- CUMS (controller, service, domain, repository, migration), gateway routing, users-api spec, generated frontend types, docs. New dependencies -- none. New endpoints -- `GET`/`PATCH /api/v1/agent-settings` (CUMS); nearest existing: EMS `/admin/settings/{key}` (cannot be extended: EMS store, and CUMS cannot call EMS) and CUMS `/users/me/settings` (per caller, not global). Reused -- `SecurityContextHelper.carriesAgentAct`/`getCurrentActor`, `UserValidationException`, `TestTokens`, PATCH-with-empty-string-clears idiom. Deviation -- a second settings store besides `AdminSettingsService`, recorded in the module map as the deliberate exception (epic decision 2026-11-21).

A typed resource rather than CUMS key/value: three fixed settings with a list and an FK each; typed columns give the organizer and FK rules to the database and make "fresh DB = off" a migration fact.

```java
@PreAuthorize("hasRole('ORGANIZER') and !@securityContextHelper.carriesAgentAct()")
public ResponseEntity<AgentSettings> updateAgentSettings(UpdateAgentSettingsRequest request) {
    return ResponseEntity.ok(agentSettingsService.update(request, securityContextHelper.getCurrentActor()));
}
```

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-4-cums.log` -- expected: BUILD SUCCESSFUL (full run, so the conformance gate checks stale lines)
- `set -o pipefail; ./gradlew :api-gateway:test --tests '*DomainRouterTest' --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-4-gateway.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:company-user-management-service:checkstyleMain :services:company-user-management-service:checkstyleTest :api-gateway:checkstyleMain :api-gateway:checkstyleTest --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-4-checkstyle.log` -- expected: BUILD SUCCESSFUL
- `cd web-frontend && npm run generate:api-types:users && npm run lint:api` -- expected: types regenerated, no NEW or STALE lint finding
- `make check-test-ratchet` -- expected: no RAISED line

## Auto Run Result

**Summary:** CUMS store and API for the organizer agent's switch, allowlist and delegate. V41 adds the singleton `agent_settings` (inserted switched off, no username seeded) and `agent_allowlist`, both FK by username to `user_profiles`. `GET` / `PATCH /api/v1/agent-settings` (users-api, contract-first) is ORGANIZER-only and refuses any token carrying `act` with 403; the PATCH is partial, every username must be an existing organizer (400 otherwise). The Spring gateway routes the path to CUMS. `AgentSettingsService.isEnabled()`, `isAllowlisted()`, `delegateUsername()` are the reads stories 5-6 use; the last two re-check ORGANIZER at call time. Module map names the store as the deliberate exception beside EMS `app_settings`.

**Files:** `docs/api/users-api.openapi.yml` (tag, path, 2 schemas); CUMS `V41__agent_settings.sql`, `AgentSettingsState`, `AgentAllowlistEntry`, two repositories, `AgentSettingsService`, `AgentSettingsController`, `UserRepository.findUsernamesWithRole`; `AgentSettingsControllerIntegrationTest` (11 tests); gateway `DomainRouter` + `DomainRouterTest`; regenerated `web-frontend/src/types/generated/user-api.types.ts`; `module-map.md`, `06-backend-architecture.md`.

**Review:** quick lens, 4 findings: 3 patched (1 medium, 2 low), 0 deferred, 1 rejected (missing-row insert race: the migration always creates the row).

**Follow-up review recommended:** false (one medium patched, no high).

**Verification:** CUMS full test + checkstyle after the patches: BUILD SUCCESSFUL, 1101 tests, 0 failures, 0 errors, 15 skipped (JUnit XML); `AgentSettingsControllerIntegrationTest` 11/11; response conformance 502 responses, baseline empty. Gateway `DomainRouterTest` 46/46 and gateway checkstyle clean (before the patches; gateway untouched by them). `lint:api` clean, spec examples test 6/6, EMS and PCS `compileJava` green, `make check-test-ratchet` no RAISED line. Not run: EMS/PCS test suites, frontend type-check, `make test`.

**Residual risks:** No Bruno coverage by design (production post-deploy run). The stored allowlist shown by GET can list a demoted organizer; only the issuing reads filter them.
