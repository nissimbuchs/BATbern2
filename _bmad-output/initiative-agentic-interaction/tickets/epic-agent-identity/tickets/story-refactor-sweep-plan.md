---
title: 'Refactor sweep of epic agent identity'
type: 'refactor'
ticket: '8'
created: '2026-11-22'
status: done
baseline_revision: 'eab0b86d12e92e32be192567ecd9a517a5bce9a2'
route: 'full'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/CLAUDE.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Stories 0.1-0.7 left duplicated checks and test helpers, dead code from the decoder consolidation, docs that overstate or misstate the built behaviour, and a second independent review (2026-11-22) found small doc/code gaps in the agent-token time check and the `/internal/agent-*` 401 behaviour.

**Approach:** Cleanup only: consolidate duplicates, delete dead code, tighten one boundary (`iat == exp`), correct docs and doc-drift mappings, add the missing tests. Every finding that needs new behaviour or an owner decision becomes a dated line in the epic Notes, not code.

## Boundaries & Constraints

**Always:** TDD for the one behaviour tightening (test first). Test fidelity rules (coding-standards.md "Test fidelity rules"): no new own-code mocks. Gradle one module at a time, `--max-workers=4`; logs to `~/.cache/batbern-test-logs/` via `set -o pipefail; ... 2>&1 | tee <log>`. Keep every HTTP status, `details.code` value, property name, env name and endpoint exactly as it is.

**Never:** Push, open a PR, touch AWS. Edit an applied migration (V13, V40-V42, V161). Change `actorOr` semantics in any service. Rename or re-map refusal code values. Decide the epic's open owner questions. Change the security filter chain or the watch REST/STOMP principals. Add the two untracked mp4 files. Run `make test` or parallel heavy builds.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| iat equals exp | agent token, `iat == exp` | refused by `AgentTokenValidator` | `invalid_token`, "iat not before exp" style message |
| iat at skew edge | `iat = now + 60 s` (fixed Clock) | accepted | none |
| iat beyond skew | `iat = now + 61 s` (fixed Clock) | refused | `invalid_token` |
| lifetime 300 s | `exp - iat = 300` | accepted (unchanged) | none |
| undecodable bearer, no caller secret | `POST /internal/agent-grants`, malformed or wrong-signature bearer, no `X-Agent-Caller-Secret` | 401 from the filter, no `details`, no grant row | positive control: same body with secret, no bearer → 201 |

</intent-contract>

## Code Map

- `shared-kernel/src/main/java/ch/batbern/shared/security/AgentTokenValidator.java:65-74` -- iat/exp rules; `AgentTokenValidator(Clock)` (:47) has no caller: use it in a new `shared-kernel/src/test/java/ch/batbern/shared/security/AgentTokenValidatorTest.java` (build `Jwt` objects with `Jwt.withTokenValue(...)`, fixed `Clock`). `AgentTokenSigner` never produces `iat == exp`.
- `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/security/TestTokens.java:45` -- `AGENT_TOKEN_LIFETIME_SECONDS` unused: delete. `TestTokens.watchToken` exists.
- Stub tables copied 4x: EMS `services/event-management-service/src/test/java/ch/batbern/events/security/AgentAttributionIntegrationTest.java:79-98`, EMS `.../mailcenter/AudienceGuardIntegrationTest.java:120-126,162-164`, PCS `services/partner-coordination-service/src/test/java/ch/batbern/partners/security/AgentAttributionIntegrationTest.java:84-120`, PCS `.../security/AudienceGuardIntegrationTest.java:100-109,145-147`. EMS has `user_profiles` (test migration) but no `role_assignments`; PCS has neither.
- Hand-rolled `watchToken()` (MACSigner): `api-gateway/src/test/java/ch/batbern/gateway/config/ProductionSecurityFilterChainTest.java:177`, CUMS `services/company-user-management-service/src/test/java/ch/batbern/companyuser/config/ProductionSecurityFilterChainTest.java:174`, EMS `services/event-management-service/src/test/java/ch/batbern/securitychain/EmsProductionSecurityFilterChainTest.java:182`.
- CUMS `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/AgentTokenService.java:44-45,87-97,130-137` duplicates `AgentGrantService.java:72-73,228-238,259-270` (switch check, allowlist check, same messages; `MAX_RUN_ID`, `MAX_SECRET`). Same package. Mint keeps its own INFO refusal log (run id, principal kind) and its order (switch → grant → allowlist → Cognito sub → key).
- `.../exception/AgentRequestRefusedException.java:22` -- constant `INVALID_REQUEST` holds `"INVALID_AGENT_REQUEST"`.
- `.../controller/InternalAgentController.java:25-31` Javadoc and `docs/api/internal/cums-internal.openapi.yml` (~:32-40 info, the 401 descriptions ~:1338, :1444, grant 401) -- say "caller secret before anything else" and "bearer that does not decode (expired, malformed, wrong signature)".
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/InternalAgentGrantIntegrationTest.java:581-596` -- `should_refuseWithFilter401_when_bearerUndecodable` (expired token, with secret only).
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/security/SecurityContextHelper.java:26` -- `getCurrentUserId()` no caller left (grep main + test before deleting).
- Gateway dead Cognito client: `api-gateway/src/main/java/ch/batbern/gateway/config/CognitoConfig.java` (only bean `cognitoIdentityProviderClient`, no consumer; password reset runs client-side), `api-gateway/build.gradle:39` (`software.amazon.awssdk:cognitoidentityprovider`), test mocks `api-gateway/src/test/java/ch/batbern/gateway/config/TestAwsConfig.java:91-115`, `TestSecurityConfig.java:97-120`; top-level `cognito:` block `api-gateway/src/test/resources/application-test.yml:51-55` (unread: grep `cognito.` `@Value`/`ConfigurationProperties` first). Keep `aws.cognito.*` (read by `ConfigController`) and `auth/model/UserContext`.
- Docs: `docs/architecture/module-map.md:24-27` (EMS client and agent-service grant wording), `:138` (`/agent-settings`); `docs/architecture/06-backend-architecture.md:139` (Watch test tokens), `:148-149` (`actorOr`), `:205` (filter 401); `.github/doc-drift-mappings.yml:214-225` (Epic 17 entry 7 comment orphaned by the `Actors`/`AudienceGuard` entries inserted at :216, :221), `:70` (CUMS catch-all).
- Epic Notes: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-identity/epic-agent-identity.md` `## Notes` (append only; do not touch existing lines).

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/.../AgentTokenValidatorTest.java` (new) -- RED first: `iat == exp` refused; `iat = now+60` accepted, `now+61` refused (fixed Clock); lifetime 300 accepted, 301 refused -- the review's boundary nit and the unused Clock constructor.
- [x] `shared-kernel/.../AgentTokenValidator.java` -- refuse `iat` not before `exp`; Javadoc says so -- mirrors the signer.
- [x] `TestTokens.java` -- delete `AGENT_TOKEN_LIFETIME_SECONDS`; add `public static final String ROLE_STUB_DDL`-style helper or a small `RoleStubTables` class in `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/security/` (`create(JdbcTemplate)`: `CREATE TABLE IF NOT EXISTS` for `user_profiles(id UUID PRIMARY KEY, cognito_user_id VARCHAR(255), username VARCHAR(100))` and `role_assignments(user_id UUID NOT NULL, role VARCHAR(50) NOT NULL)`; `grant(jdbc, username, sub, role)`; `remove(jdbc, subs...)`) -- one home for the 4 copies; check `spring-jdbc` is on testFixtures' classpath, else take plain SQL strings.
- [x] The 4 EMS/PCS ITs above -- use the helper; keep each test's own cleanup of its domain tables -- dedupe.
- [x] The 3 chain slice tests -- use `TestTokens.watchToken` instead of their `MACSigner` copy, only where the fixture produces the same claims/secret (read it); otherwise leave and note -- 06:139 already claims this.
- [x] `AgentGrantService.java` / `AgentTokenService.java` -- package-private `requireSwitchedOn()`, `requireAllowlisted(username)` and length constants in `AgentGrantService`, used by the mint service; mint still logs its refusal with run id/principal kind before rethrow -- remove duplication, same codes/messages/order.
- [x] `AgentRequestRefusedException.java` + usages -- rename constant `INVALID_REQUEST` → `INVALID_AGENT_REQUEST` (value unchanged).
- [x] `InternalAgentGrantIntegrationTest.java` -- add a case: no caller secret + malformed bearer (`"not-a-jwt"`) and + wrong-signature agent token (sign with a fresh RSA key) → 401, empty body/no `details`, no row; positive control as in the existing test -- review finding C6.
- [x] `cums-internal.openapi.yml`, `InternalAgentController.java` Javadoc, `06-backend-architecture.md:205` -- filter 401 = "a bearer token the decoder does not accept (any kind, any validation failure, e.g. an agent token while CUMS's public key is `none`)"; mention Spring sets `WWW-Authenticate` with an `error_description`; narrow "caller secret first" to "first among the operation's checks; a body Spring cannot read (malformed JSON, wrong type, wrong Content-Type) is 400/415 before it"; one sentence why grants answer `LINKED_RUN_INVALID` and mint `GRANT_INVALID` for an unknown/expired grant (different operations, different caller meaning) -- review C3-C5, survey §2. Then `cd web-frontend && npm run lint:api-internal`.
- [x] `PCS SecurityContextHelper.java` -- delete `getCurrentUserId()` if no caller.
- [x] Gateway -- delete `CognitoConfig` (or its bean), the dependency, both mock `CognitoIdentityProviderClient` beans and the unread test `cognito:` block; infrastructure (IAM) stays untouched -- dead since password reset moved client-side.
- [x] `module-map.md` -- 24-27: EMS *will* call `/internal/agent-grants`/`-eligibility` (epic speaker mail agent; no client yet, its caller secret is injected but unread); agent service requests organizer-login, linked-run and scheduler grants and mints tokens; :138 `/api/v1/agent-settings`.
- [x] `06-backend-architecture.md:148-149` -- state that `actorOr` differs per service (CUMS: JWT principal only; EMS/PCS: any signed-in non-anonymous principal) as documented in story 0.2.
- [x] `.github/doc-drift-mappings.yml` -- move the `Actors`/`AudienceGuard` entries so the Epic 17 entry 7 comment sits above its `shared/contract/` entry again; add: `shared-kernel/src/main/java/ch/batbern/shared/security/{BatbernJwtDecoder,AgentTokenSigner,AgentTokenValidator,TokenKind}.java` → 06, 08; CUMS `**/companyuser/**/Agent*.java` + `InternalAgentController.java` → 06, module-map; `scripts/deploy/create-agent-signing-key.sh` and `infrastructure/lib/stacks/secrets-stack.ts` → `docs/operations/runbooks.md`. Follow the file's existing entry format exactly.
- [x] Epic Notes -- append dated (2026-11-22) lines for each item in Design Notes "Recorded, not built".

**Acceptance Criteria:**
- Given the sweep is done, when `git grep -n "AGENT_TOKEN_LIFETIME_SECONDS\|INVALID_REQUEST\b\|cognitoIdentityProviderClient"` runs on main and test sources, then nothing matches except the renamed constant's new name.
- Given an agent token with `iat == exp`, when any service decodes it, then it is refused.
- Given every module's full suite, checkstyle, ratchet, API lints and type-check run once at the end, then all pass with no RAISED ratchet line and empty response-conformance baselines.
- Given the epic Notes, when read, then every deferred or recorded finding of this sweep has a dated line.

## Implementation Notes

- `iat == exp`: Spring's `Jwt` constructor asserts `exp` after `iat`, so the decoder already refused such a token (`BadJwtException`, 401) before the sweep, and a validator unit test cannot build that `Jwt`. The case is pinned through the shared decoder in `AgentTokenValidatorTest` (green before and after the validator change, so no true RED); the validator rule is now strict (`!iat.isBefore(exp)`, "iat not before exp") so it is complete on its own. Skew edge (60/61 s) and lifetime (300/301 s) are unit tests on a fixed `Clock`.
- `RoleStubTables` lives in shared-kernel testFixtures (spring-jdbc is on its classpath via `spring-boot-jdbc-test`); the 4 EMS/PCS ITs use it, PCS's private `grant` helper is gone.
- The 3 chain slice tests produced exactly `TestTokens.watchToken`'s claims (issuer `batbern-watch`, sub `test.organizer`, role `ORGANIZER`, 300 s, HS256 with their own secret): all three now call it.
- `AgentTokenService` no longer needs `AgentSettingsService` (both checks go through `AgentGrantService`); its INFO refusal log and check order are unchanged.
- C6 test also asserts `WWW-Authenticate` starts with `Bearer` and carries `error="invalid_token"`, the behaviour the docs now describe.
- Gateway: kept `software.amazon.awssdk:auth` and the BOM (test `AwsBasicCredentials`, `cloudwatchlogs`); only `cognitoidentityprovider` removed.
- doc-drift: fnmatch has no brace expansion, so the four shared-kernel files are four entries; CUMS `*/Agent*.java` is one fnmatch entry plus one for `InternalAgentController.java`. `.github/scripts/test-doc-drift.sh`: 35 passed.
- AC 1 grep still matches CUMS `cognitoIdentityProviderClient` (CUMS's live Cognito client, out of scope) and shared `ErrorCode.ERR_INVALID_REQUEST` (`INVALID_REQUEST\b` without a leading `\b`); nothing of this sweep's scope matches.

## Plan Change Log

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 4 findings — high 0, medium 0, low 4, false 0, maybe-false 0
- findings:
  - `[low]` `[patch]` `InternalAgentController` Javadoc said a missing body answers 401; with a valid secret it is the operation's 400 `INVALID_AGENT_REQUEST` — fixed: only a missing secret answers 401 before the other checks, a missing body the operation's 400 after it.
  - `[low]` `[reject]` the strict `iat`-before-`exp` branch of `AgentTokenValidator` is unreachable (Spring's `Jwt` refuses `iat >= exp` while the decoder builds it) and the matrix row's "refused by `AgentTokenValidator`" is met by Spring instead; no true RED — the observable outcome (refused in every service) holds and is pinned through the real decoder with a positive control; the branch is a defensive one-condition change, the fix would be editing the plan's matrix. Recorded in the epic Notes.
  - `[low]` `[reject]` AC 1's grep still matches CUMS's live `cognitoIdentityProviderClient` and shared `ERR_INVALID_REQUEST` — the AC wording is too wide (fix = edit this build's plan); no match lies in the sweep's scope (Implementation Notes).
  - `[low]` `[patch]` C6 test asserted only "no details" though spec/06/Javadoc promise an empty body — fixed: asserts the body is empty.

## Design Notes

Architecture note: modules touched -- shared-kernel security + test fixtures, gateway config (dead code), CUMS agent services (internal refactor), EMS/PCS tests, PCS helper, cums-internal spec (descriptions), docs. New dependencies -- none (one removed: gateway `cognitoidentityprovider`). New endpoints -- none. Reused -- `TestTokens`, `AgentGrantService` checks. Deviations -- none.

Recorded, not built (owner or new story): (1) user merge drops the source's allowlist row and delegate (FK cascade / set null, V41) instead of moving them; (2) watch REST paths (`LiveTimingController`, `WatchEventController.confirmArrival`) pass the JWT `sub`, so Cognito organizers are recorded and polled by UUID (pre-existing; presence is keyed on the STOMP principal, which is also the sub, so changing one side alone desyncs presence); (3) `actorOr` differs per service (documented, not aligned); (4) `/internal/agent-*` filter 401 carries Spring's `WWW-Authenticate error_description` (documented; a custom entry point would be a behaviour change); (5) a body Spring cannot read answers 400/415 before the caller-secret check (documented; binding raw would be a behaviour change); (6) a mint refusal is logged twice (service INFO with run id, handler WARN): kept, the service line carries context the handler lacks; (7) story-number scheme in Javadoc mixed (0.N vs N): cosmetic, left; (8) EMS/PCS `getCurrentActor`/`carriesAgentAct` test-only: kept for the uniform helper API the module map names; (9) narrowing public constants on `BatbernJwtDecoder`/`Actors`: left (shared-kernel public API, no gain); (10) consolidating CUMS IT helpers (`user`, `perform`, `switchOff` in three test classes): left, CUMS-only churn with no defect.

## Verification

Implementer (targeted, one at a time): `./gradlew :shared-kernel:test --tests '*AgentTokenValidatorTest' --tests '*BatbernJwtDecoderTest' --max-workers=4`, then the touched ITs per module with `--tests`, then `:<module>:compileTestJava` for gateway. The orchestrator runs the full list below afterwards.

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test :shared-kernel:publishToMavenLocal --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-8-shared-kernel.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :api-gateway:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-8-gateway.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-8-cums.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-8-ems.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:partner-coordination-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-8-pcs.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew checkstyleMain checkstyleTest checkstyleTestFixtures --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-8-checkstyle.log` -- expected: BUILD SUCCESSFUL (drop an unknown task name)
- `make check-test-ratchet` -- expected: no RAISED; STALE → `--update` and commit baseline
- `cd web-frontend && npm run lint:api-internal && npm run lint:api && npm run check:api-types && npm run type-check` -- expected: clean, no diff
- `cd infrastructure && ../gradlew --stop; rm -rf /tmp/cdk.out*; npx jest --maxWorkers=2 test/unit/agent-signing-key.test.ts test/unit/agent-signing-key-script.test.ts test/unit/secrets-stack.test.ts test/unit/company-management-stack.test.ts test/unit/ems-task-role-grants.test.ts` -- expected: all pass

## Auto Run Result

**Summary:** Cleanup sweep of epic agent identity, no new behaviour. A second, independent review of 0.1's agent-token time checks, 0.2's PCS user-merge SQL and watch wrapping, and 0.5's filter 401 found no high or medium defect; its low findings were fixed (docs, one test) or recorded. Consolidated: CUMS mint reuses the grant service's switch/allowlist checks and length constants; `RoleStubTables` test fixture replaces four copies; three chain tests use `TestTokens.watchToken`. Dead code removed: gateway `CognitoConfig`, its `cognitoidentityprovider` dependency, two mock Cognito beans and an unread test config block; PCS `getCurrentUserId`; `TestTokens.AGENT_TOKEN_LIFETIME_SECONDS`. `INVALID_REQUEST` constant renamed `INVALID_AGENT_REQUEST` (value unchanged). `AgentTokenValidator` refuses `iat` not before `exp` (Spring already did). Docs: filter-401 and caller-secret-first wording (spec, Javadoc, 06), `WWW-Authenticate` header, `LINKED_RUN_INVALID` vs `GRANT_INVALID`, `actorOr` per service, module map (EMS has no grant client yet, agent-service grants, `/api/v1/agent-settings`), doc-drift mappings for the new security, CUMS agent, script and secrets-stack files (orphaned comment fixed). Epic Notes: 2 open questions, 9 recorded lines.

**Files:** shared-kernel `AgentTokenValidator`, new `AgentTokenValidatorTest`, testFixtures `TestTokens`, new `RoleStubTables`; gateway `build.gradle`, `CognitoConfig` (deleted), `TestAwsConfig`, `TestSecurityConfig`, `application-test.yml`, `ProductionSecurityFilterChainTest`; CUMS `AgentGrantService`, `AgentTokenService`, `AgentRequestRefusedException`, `InternalAgentController`, `InternalAgentGrantIntegrationTest`, `ProductionSecurityFilterChainTest`; EMS `AgentAttributionIntegrationTest`, `AudienceGuardIntegrationTest`, `EmsProductionSecurityFilterChainTest`; PCS `SecurityContextHelper`, `AgentAttributionIntegrationTest`, `AudienceGuardIntegrationTest`; `cums-internal.openapi.yml`; docs 06, module-map; `.github/doc-drift-mappings.yml`; epic Notes.

**Review:** quick lens, 4 findings: 2 patched (low), 0 deferred, 2 rejected (low: unreachable defensive validator branch, refusal pinned through the decoder; low: AC grep wording wider than the scope). Independent pre-review (fresh eyes, read-only): 0 high, 0 medium; its lows fixed (docs C3-C5, test C6, `iat == exp`) or recorded (merge of allowlist/delegate, watch REST `sub`, `WWW-Authenticate` content).

**Follow-up review recommended:** false (no high or medium patched).

**Verification (JUnit XML, logs `~/.cache/batbern-test-logs/0-8-v-*.log`):** shared-kernel 815/0 failures; gateway 334/0 (26 skipped); CUMS 1204/0 (15 skipped), re-run after the review patches 1204/0 + checkstyle; EMS 3158/0 (6 skipped); PCS 321/0; checkstyle main+test all modules + shared-kernel testFixtures clean; `make check-test-ratchet` no RAISED/STALE; `lint:api-internal` clean, `lint:api` 80 findings (unchanged, no NEW), `check:api-types` no diff, `type-check` clean; CDK 5 suites / 36 tests passed.

**Residual risks:** nothing ran against local dev or AWS. The 400/415-before-secret claim for unreadable bodies is documented from Spring's behaviour, not tested. `make verify` itself was not run (it includes `make test`, forbidden on rack); its parts were run individually.
