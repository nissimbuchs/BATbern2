---
title: 'CUMS mints delegated tokens that every service accepts'
type: 'feature'
ticket: '6'
created: '2026-11-22'
status: 'built'
baseline_revision: 'c9dca63c948c1220d4b2be9c2d758afa2bf94cfd'
route: 'full'
route_source: 'auto'
risk: 'high'
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

**Problem:** Stories 0.1-0.5 built the decoder, the actor resolver, the key secrets and the run grants, but nothing signs an agent token: agent-service has no way to act, and the cross-service acceptance (R3) is proven only with test-fixture tokens that no production code produces.

**Approach:** Contract-first `POST /internal/agent-tokens` in `cums-internal.openapi.yml`. One signer class in shared-kernel (`AgentTokenSigner`) produces the fixed claims; CUMS uses it with `agent.jwt.private-key`, and the test fixture `TestTokens.agentToken` uses the same class with the runtime test key, so every service's existing agent-token tests run on tokens of the production signer. CUMS mints only for agent-service's caller secret, against `AgentGrantService.findActiveGrant`, re-checking switch and allowlist at mint time.

## Boundaries & Constraints

**Always:**
- Request: header `X-Agent-Caller-Secret` = **agent-service** secret only (EMS never mints; AD-3, module map). Body exactly `{runId, grantSecret}` (runId 1..36, grantSecret 1..100; unknown field or missing field 400).
- Check order, first failure answers: caller secret (401 `CALLER_SECRET_INVALID`) → body (400 `INVALID_AGENT_REQUEST`) → switch (403 `AGENT_SWITCHED_OFF`) → grant (403 `GRANT_INVALID`: unknown run, wrong secret, expired) → principal (403 `PRINCIPAL_NOT_ELIGIBLE`: `AgentSettingsService.isAllowlisted(grant.username)` false, or the user has no `cognito_user_id`) → signing key (503 `AGENT_SIGNING_KEY_MISSING`: property unset, blank or `none` trimmed/any case). Same `AgentRequestRefusedException` + `details.code` as story 5. Never a 500 for any of these.
- Response 200 `{token, expiresAt}`. Claims fixed: header `alg=RS256`, `typ=JWT`; `iss=batbern-agent`; `sub` = `user_profiles.cognito_user_id` of the grant's username (JwtRolesConverter looks roles up by `cognito_user_id = sub`); `custom:username` = grant username; `act = {run: grant.runId, principal: <principal kind lowercase: requester | organizer | organizer_delegate>}`; `iat` = now (seconds); `exp` = min(iat + `AgentTokenValidator.MAX_LIFETIME`, grant `expiresAt`); no `custom:role`/`role`, nothing else.
- `AgentTokenSigner` (shared-kernel main, `ch.batbern.shared.security`): constructed from an `RSAPrivateKey` and a `Clock`; static `parsePrivateKey(String)` accepts PKCS#8 PEM (header/footer, newlines) or bare base64 DER; an unparseable non-placeholder key fails CUMS startup (`IllegalStateException`, as the decoder does for the public key). Lifetime capped by `AgentTokenValidator.MAX_LIFETIME`; a `notAfter` not after now → `IllegalArgumentException`.
- Logs: mint and refusal at INFO with run id, principal kind and code; never the token, grant secret, caller secret or key.
- TDD; Testcontainers via `AbstractIntegrationTest` + `@Import(TestAwsConfig.class)`; production chain; no class-level `@Transactional`; cleanup in `@AfterEach` like `InternalAgentGrantIntegrationTest`; absence assertions with a positive control; responses checked with `ProviderContract.assertResponseMatchesSpec` incl. non-2xx; production-shaped names (umlaut/apostrophe). Checkstyle clean. Test keys generated at runtime only.
- rack rules: one Gradle module at a time, `--max-workers=4`, logs via `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/0-6-<name>.log`, grep the log, never re-run a suite to grep; no `make test`.

**Never:** No change to the decoder's verification rules, `JwtRolesConverter`, the filter chain (`/internal/**` stays `permitAll`), CDK, or `AgentGrantService` behaviour beyond reuse. No caller code in EMS or agent-service. No migration (grants already exist; minting stores nothing). No committed private key. No Bruno file. No git push, no PR, no AWS call; do not add `film-batbern61/*.mp4`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Mint | switch on, allowlisted organizer with sub, active EMS_HANDOFF grant, agent-service secret | 200, token decodes with the real `JwtDecoder`, claims exactly as Always, exp − iat ≤ 300 s | — |
| Token used | minted token as Bearer on `POST /api/v1/companies` | 201, `companies.created_by = agent:<run> for <username>` (role ORGANIZER from `role_assignments` by sub) | — |
| Grant near expiry | grant expires in < 300 s | exp = grant expiresAt | — |
| Caller secret | missing, wrong, EMS's secret | 401 `CALLER_SECRET_INVALID` | — |
| Bad body | missing runId / grantSecret, unknown field, too long | 400 `INVALID_AGENT_REQUEST` | — |
| Switch off | grant issued while on, switch then off | 403 `AGENT_SWITCHED_OFF` | — |
| Grant | unknown run, wrong secret, expired row | 403 `GRANT_INVALID` | — |
| Principal | removed from allowlist (or lost ORGANIZER) after grant; user without `cognito_user_id` | 403 `PRINCIPAL_NOT_ELIGIBLE` | — |
| No key | `agent.jwt.private-key` = `none` | 503 `AGENT_SIGNING_KEY_MISSING`, valid otherwise | — |
| Signer unit | PEM / bare DER / unset / blank / `none` / garbage | parse / parse / off / off / off / `IllegalStateException` | — |

</intent-contract>

## Code Map

- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/AgentGrantService.java` -- `findActiveGrant(runId, secret)` seam (reuse, unchanged); its `requireSwitchedOn`/`requireAllowlisted` logic is private: re-implement the two checks in the mint service via `AgentSettingsService.isEnabled()` / `isAllowlisted(username)`.
- `.../controller/InternalAgentController.java` -- add `POST /internal/agent-tokens` beside the grant mapping (header and body `required = false`, same reason as story 5); update class Javadoc.
- `.../security/AgentCallerSecrets.java` -- `matches(Caller.AGENT_SERVICE, presented)`.
- `.../exception/AgentRequestRefusedException.java` -- add `GRANT_INVALID`, `AGENT_SIGNING_KEY_MISSING` (503); handler in `GlobalExceptionHandler` already renders status + `details.code`; check severity mapping for 503 is sane.
- `.../dto/AgentGrantRequest.java` -- model for the new request record (`@JsonAnySetter` unknown fields).
- `.../domain/AgentGrant.java` -- `getRunId`, `getUsername`, `getPrincipalKind`, `getExpiresAt`; `AgentPrincipalKind` enum (lowercase via its converter).
- `.../repository/UserRepository.java` -- `findByUsername` → `User.getCognitoUserId()`.
- `shared-kernel/src/main/java/ch/batbern/shared/security/` -- new `AgentTokenSigner`; reuse `BatbernJwtDecoder.AGENT_ISSUER`, `AgentTokenValidator.MAX_LIFETIME`; parse helper style of `BatbernJwtDecoder.parsePublicKey`.
- `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/security/TestTokens.java` -- `agentToken(...)` delegates to `AgentTokenSigner` with the JVM test key; add `agentPrivateKeyPem()` (PKCS#8 PEM); the `agent(...)` builder for broken variants stays.
- `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/AbstractIntegrationTest.java:96-102` -- also register `agent.jwt.private-key` from `TestTokens.agentPrivateKeyPem()` (only CUMS reads it), so CUMS ITs mint with the key the decoder trusts.
- `docs/api/internal/cums-internal.openapi.yml` -- add `/internal/agent-tokens` after `/internal/agent-eligibility` (style of 1122-1368), schemas `AgentTokenRequest`, `AgentToken`; info paragraph (line 32) names the third operation.
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/InternalAgentGrantIntegrationTest.java` -- seeding (users with sub + ORGANIZER, allowlist, switch on), cleanup SQL, grant issuing helper: copy the pattern.
- `.../contract/S2sProviderContractIntegrationTest.java:114-115` -- add `"POST, /internal/agent-tokens"`.
- Existing cross-service tests that now run on signer-made tokens (keep green, no edits needed): gateway `config/ProductionSecurityFilterChainTest`, CUMS `security/AgentAttributionIntegrationTest`, EMS `security/AgentAttributionIntegrationTest`, `securitychain/EmsProductionSecurityFilterChainTest`, `security/JwtStompInterceptorTest`, PCS `security/AgentAttributionIntegrationTest`, `config/PcsProductionSecurityFilterChainTest`, shared-kernel `BatbernJwtDecoderTest`, `ActorsTest`.
- Docs: `docs/architecture/06-backend-architecture.md` (after "Agent grants", ~line 170-200: new "Agent tokens" paragraph; line 196 "story 6" wording), `docs/architecture/module-map.md:23-30` (agent-tokens caller = agent-service), `docs/operations/runbooks.md:353-363` (private key consumer `/internal/agent-tokens`; agent-service secret row adds it; 503 code when `none`).

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/src/test/java/ch/batbern/shared/security/AgentTokenSignerTest.java` -- RED first: signed token decodes with a `BatbernJwtDecoder` built for the matching public key; exact claim set and header; exp capped by notAfter; notAfter ≤ now refused; parse rows of the matrix (PEM, DER, garbage); fixed `Clock`.
- [x] `shared-kernel/src/main/java/ch/batbern/shared/security/AgentTokenSigner.java` -- implement (Nimbus `RSASSASigner`, already on the classpath) -- GREEN.
- [x] `TestTokens.java`, `AbstractIntegrationTest.java` -- as in Code Map; Javadoc: default agent tokens come from the production signer.
- [x] `docs/api/internal/cums-internal.openapi.yml` -- spec first: 200/400/401/403/503, codes and check order documented, examples with invented usernames; `cd web-frontend && npm run lint:api-internal` clean.
- [x] `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/InternalAgentTokenIntegrationTest.java` -- RED first: one test per matrix row (mint, token used on `POST /api/v1/companies`, near-expiry cap, each caller-secret case, each bad body, switch off after grant, unknown run, wrong secret, expired grant via SQL moving `issued_at`/`expires_at` into the past, removed from allowlist, no `cognito_user_id`), plus `should_mintSameClaimShape_as_TestTokens` (header + claim names of a minted token equal those of `TestTokens.agentToken`), refusals with positive controls.
- [x] `.../controller/InternalAgentTokenKeyMissingIntegrationTest.java` -- own context overriding `agent.jwt.private-key` to `none` (prove the override takes effect: the valid request answers 503 `AGENT_SIGNING_KEY_MISSING`, a request with a bad caller secret still 401).
- [x] `.../service/AgentTokenService.java`, `.../dto/AgentTokenRequest.java`, `AgentTokenResponse.java`, controller mapping, exception codes -- implement -- GREEN.
- [x] `S2sProviderContractIntegrationTest` -- examples list.
- [x] Docs 06, module-map, runbook -- as in Code Map.

**Acceptance Criteria:**
- Given an allowlisted organizer with a Cognito sub and an active grant, when agent-service posts the run id and grant secret, then a token comes back whose claims are exactly the fixed set, which the CUMS chain accepts and records as `agent:<run> for <username>`.
- Given `TestTokens.agentToken` is produced by `AgentTokenSigner`, when the gateway, CUMS, EMS and PCS agent-token tests run, then they pass, with EMS and PCS ITs recording the agent actor and resolving ORGANIZER from `role_assignments` by sub.
- Given any refusal condition of the matrix, when a token is requested, then the matrix status and `details.code` come back and no token is in the body.
- Given `agent.jwt.private-key` = `none`, when a valid mint request arrives, then 503 `AGENT_SIGNING_KEY_MISSING`, and CUMS starts normally.

## Implementation Notes

- `AgentTokenSigner.sign(sub, username, run, principal, notAfter)` returns `SignedAgentToken(token, issuedAt, expiresAt)`; `fromKey(value, clock)` gives empty for unset/blank/`none`, throws `IllegalStateException` for garbage (message names the property, never the value). A `notAfter` that leaves no whole second after `iat` is refused (`IllegalArgumentException`); `AgentTokenService` maps that race (grant ends between lookup and signing) to 403 `GRANT_INVALID`, never 500.
- `GlobalExceptionHandler`: severity of an agent refusal is HIGH for 503, MEDIUM for 401, LOW otherwise.
- RED runs: shared-kernel did not compile without the signer; CUMS `*InternalAgentToken*` failed all new tests before the endpoint existed (log `0-6-cums-red.log`).

## Plan Change Log

- `InternalAgentTokenKeyMissingIntegrationTest` overrides the key with an `ApplicationContextInitializer` (`addFirst` property source), not a second `@DynamicPropertySource`: measured, the subclass `@DynamicPropertySource` lost to the base class's registration (`environment.getProperty` returned the test key, 2 tests red). `should_holdPlaceholder_when_overridden` asserts the override.

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 4 findings — high 0, medium 1, low 3, false 0, maybe-false 0
- findings:
  - `[low]` `[patch]` A valid PKCS#8 RSA key under 2048 bits failed CUMS startup with Nimbus's `IllegalArgumentException` from `new RSASSASigner`, not the documented `IllegalStateException` naming `agent.jwt.private-key` — `fromKey` maps it to the same `IllegalStateException` (`NOT_A_KEY`); test `should_failStartup_when_keyTooShort` (runtime RSA-1024).
  - `[low]` `[patch]` Public `parsePrivateKey(null)` threw `NullPointerException` against its Javadoc — null check, test `should_failStartup_when_parseNull`.
  - `[medium]` `[patch]` Every `TestTokens.agentToken` caller passed a free-form `act.principal` (`organizer-assistant`, a username) that CUMS never mints, so "the services' tests run on exactly what CUMS mints" held for claim names only — all callers (gateway, CUMS, EMS, PCS, shared-kernel) now pass `organizer`; `should_mintSameClaimShape_as_TestTokens` also asserts the minted principal is a lowercased `AgentPrincipalKind`.
  - `[low]` `[patch]` Two edited doc lines (cums-internal agent-grants description, module-map) ran ~120 characters — re-wrapped.

## Design Notes

Architecture note: modules touched -- shared-kernel security (signer) + test fixtures, CUMS (controller, service, dto, exception), cums-internal spec, docs. New dependencies -- none (CUMS reads its own grants, settings and users). New endpoint -- `POST /internal/agent-tokens`; nearest existing `POST /internal/agent-grants`: a grant is a stored hour-long credential with its own trigger rules, a token is a 5-minute signed artifact re-minted per write (AD-3), so folding minting into the grant call would make grants re-issue on every write. Reused -- `AgentGrantService.findActiveGrant`, `AgentSettingsService`, `AgentCallerSecrets`, `AgentRequestRefusedException` + handler, `ProviderContract`, `TestTokens`. Deviation -- the agent-service may call this `/internal` operation (AD-2 exception, already in module-map); no new one.

Why the signer lives in shared-kernel main though only CUMS holds a key: it is the single place the claim set is defined, and the test fixture must use the same class so the per-service tests (gateway, EMS, PCS) prove acceptance of exactly what CUMS mints without a cross-process test. Verifiers hold only the public key, so the class grants nothing by itself.

Why the switch is checked before the grant: same as story 5, an unauthorised probe of run ids learns nothing while off; re-checking switch and allowlist on every mint is what makes "switch off stops every path within one token lifetime" true (AD-10).

Why 503 for a missing key: the request is valid and allowed, the service is not configured yet (deploy placeholder); 503 is distinguishable from policy refusals in alarms.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-6-shared-kernel.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-6-cums.log` -- expected: BUILD SUCCESSFUL (full run: conformance stale check)
- `set -o pipefail; ./gradlew :api-gateway:test --tests '*ProductionSecurityFilterChainTest' --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-6-gateway.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:event-management-service:test --tests '*AgentAttributionIntegrationTest' --tests '*EmsProductionSecurityFilterChainTest' --tests '*JwtStompInterceptorTest' --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-6-ems.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:partner-coordination-service:test --tests '*AgentAttributionIntegrationTest' --tests '*PcsProductionSecurityFilterChainTest' --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-6-pcs.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :shared-kernel:checkstyleMain :shared-kernel:checkstyleTest :shared-kernel:checkstyleTestFixtures :services:company-user-management-service:checkstyleMain :services:company-user-management-service:checkstyleTest --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-6-checkstyle.log` -- expected: BUILD SUCCESSFUL (drop a task name Gradle does not know)
- `cd web-frontend && npm run lint:api-internal` -- expected: no error
- `make check-test-ratchet` -- expected: no RAISED line

## Auto Run Result

**Summary:** CUMS mints delegated agent tokens, `POST /internal/agent-tokens` (`cums-internal` spec first), for the agent-service caller secret only, against `AgentGrantService.findActiveGrant`. Order: caller secret 401 → body 400 → switch 403 `AGENT_SWITCHED_OFF` → grant 403 `GRANT_INVALID` (unknown, wrong secret, expired) → principal 403 `PRINCIPAL_NOT_ELIGIBLE` (not allowlisted / no longer organizer / no `cognito_user_id`) → key 503 `AGENT_SIGNING_KEY_MISSING` (unset, blank, `none`). Switch and allowlist re-checked on every mint. Claims from the new shared-kernel `AgentTokenSigner`: RS256/JWT, `iss=batbern-agent`, `sub` = the user's `cognito_user_id`, `custom:username`, `act={run, principal kind lowercase}`, `iat`, `exp` = min(iat+300 s, grant end), nothing else. `TestTokens.agentToken` signs with the same class and the runtime test key (registered for every IT as `agent.jwt.private-key`), so gateway, CUMS, EMS and PCS agent-token tests run on production-shaped tokens; in CUMS a token minted over HTTP is used on `POST /api/v1/companies` and recorded as `agent:<run> for <username>`.

**Files:** shared-kernel `AgentTokenSigner` + test, `TestTokens`, `AbstractIntegrationTest`; CUMS `AgentTokenService`, `AgentTokenRequest`/`AgentTokenResponse`, `InternalAgentController`, `AgentRequestRefusedException` (2 codes), `GlobalExceptionHandler` (503 severity HIGH), `AgentGrantService` (Javadoc); tests `InternalAgentTokenIntegrationTest`, `InternalAgentTokenKeyMissingIntegrationTest`, `S2sProviderContractIntegrationTest` examples, principal value in existing agent-token tests of all four services; spec `cums-internal.openapi.yml`; docs 06, module-map, runbook.

**Review:** quick lens, 4 findings: 4 patched (1 medium, 3 low), 0 deferred, 0 rejected.

**Follow-up review recommended:** false (first pass, no high, one medium patched).

**Verification (orchestrator, after patches, JUnit XML):** shared-kernel full 805 tests / 0 failures + checkstyle main/test/testFixtures; CUMS full 1202 / 0 failures / 15 skipped + checkstyle main/test (conformance stale check ran, BUILD SUCCESSFUL); gateway `ProductionSecurityFilterChainTest` 32/0 + checkstyleTest; EMS agent attribution, chain slice, STOMP 89/0 + checkstyleTest; PCS agent attribution, chain slice 8/0 + checkstyleTest. `npm run lint:api-internal` clean; `make check-test-ratchet` no RAISED/STALE. Logs `~/.cache/batbern-test-logs/0-6-v-*.log`.

**Residual risks:** no caller exists yet (agent-service, epic agent runtime), so the contract is proven on the provider side only. The cross-service proof is per-service tests on tokens of the same signer class and key shape, not one token travelling through four running services. Full EMS/PCS/gateway suites were not run (only their agent-token classes; the change touches only test principal values there). Nothing ran against AWS; the production key stays `none` (503) until the owner runs story 3's script. `act.principal` = principal kind is a reading (Open question in the epic).
