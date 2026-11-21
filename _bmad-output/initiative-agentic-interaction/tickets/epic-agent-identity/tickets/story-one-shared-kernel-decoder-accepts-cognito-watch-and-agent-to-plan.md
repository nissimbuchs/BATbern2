---
title: 'One shared-kernel decoder accepts Cognito, Watch and agent tokens in every service'
type: 'refactor'
ticket: '1'
created: '2026-11-21'
status: 'built'
baseline_revision: 'b8047555258cb84d3fc3c76b47e002940534b32f'
route: 'full'
route_source: 'auto'
risk: 'high'
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

**Problem:** Gateway, CUMS and EMS each carry a hand-copied Cognito+Watch decoder (iss sniffed by `String.contains`), PCS has a Cognito-only one, and the gateway has a second, unused Cognito-only `jwtDecoder` bean plus a dead auth0-based `CognitoJWTValidator` chain. The agentic pilot needs a third token kind (agent, RS256, `iss=batbern-agent`) accepted everywhere; adding it to four copies would multiply the riskiest code in the system.

**Approach:** One `JwtDecoder` in `shared-kernel` (`ch.batbern.shared.security`) that routes by the unverified `iss` to a Cognito, Watch or agent verifier, each kind enabled per service by configuration (service default in code, `batbern.security.token-kinds` overrides; agent additionally needs `agent.jwt.public-key`, otherwise off). All four services use it as their only `JwtDecoder` bean; the copies and the gateway's stray decoder/validator chain are deleted.

## Boundaries & Constraints

**Always:** Cognito and Watch validation behave exactly as today (same JWK/issuer-uri choice, same default validators, same Watch HS256 secret property `watch.jwt.secret` and its dev default); the gateway keeps jwk-set-uri first, then issuer-uri; PCS default kinds are Cognito + agent, never Watch; gateway/CUMS/EMS default Cognito + Watch + agent. Signature is always verified by the selected verifier; the iss peek only routes. Test keys are generated at test runtime; no private key is committed. TDD; checkstyle clean; one heavy Gradle job at a time (`--max-workers=4`, logs via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`).

**Never:** No actor resolver or audit change (story 2), no CDK/secret wiring (story 3), no grant/mint endpoints, no change to `JwtRolesConverter`, no change to `JwtStompInterceptor` code (it already takes the `JwtDecoder` bean), no edits to applied Flyway migrations, no git push/PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Cognito token | iss = Cognito pool, COGNITO on | decoded by JWK-set verifier as today | invalid → `JwtException` → 401 |
| Watch token | iss=batbern-watch, HS256 with `watch.jwt.secret`, WATCH on | decoded | wrong secret → 401 |
| Watch token in PCS | WATCH off | refused, never sent to Cognito verifier | `BadJwtException` → 401 |
| Agent token | RS256 signed by the key matching `agent.jwt.public-key`, iss=batbern-agent, sub, `custom:username`, `act={run,principal}`, iat, exp ≤ iat+300 s, no role claim | decoded | — |
| Agent kind off | no/blank public key, or AGENT not in kinds | refused | `BadJwtException` → 401 |
| Agent token invalid | other key; HS256 alg (key confusion); expired; exp−iat > 300 s; missing iat/exp/`custom:username`/`act.run`/`act.principal`; carries `custom:role` or `role` | refused | `JwtException` → 401 |
| Malformed | not a JWT / unparseable payload | refused | `BadJwtException` |
| Public key formats | PEM with header/footer and newlines, or bare base64 X.509 DER | both parse | unparseable key → `IllegalStateException` at startup |

</intent-contract>

## Code Map

- `shared-kernel/src/main/java/ch/batbern/shared/security/` -- new home: `TokenKind` (COGNITO, WATCH, AGENT), `BatbernJwtDecoder` (`fromEnvironment(Environment, Set<TokenKind> serviceDefault)`), agent claims validator. Nimbus/oauth2-resource-server already on shared-kernel classpath (`build.gradle:50`).
- `api-gateway/.../config/SecurityConfig.java:41-95` -- `compositeJwtDecoder` + `buildCognitoDecoder` + `isWatchJwt` → replace by `@Bean @Profile("!test") JwtDecoder jwtDecoder(Environment)`; chain line 336 uses it. Test profile keeps `TestSecurityConfig.mockJwtDecoder` (@Primary).
- `api-gateway/.../config/CognitoConfig.java` -- delete `jwtDecoder()` (72-87), `cognitoJWKSProvider`, `cognitoJWTValidator`, `userPoolClientId`; keep `cognitoIdentityProviderClient`.
- `api-gateway/.../auth/{CognitoJWTValidator,CognitoJWKSProvider,DefaultCognitoJWKSProvider,UserContextExtractor}.java`, `auth/exception/*`, tests `auth/{CognitoJWTValidatorTest,UserContextExtractorTest,TestKeyPairGenerator}.java` -- dead chain (only referenced by each other); delete, then drop `com.auth0:java-jwt` and `com.auth0:jwks-rsa` from `api-gateway/build.gradle:43-44` if nothing else imports `com.auth0`. Keep `auth/model/UserContext` (used by routing/rate limiting).
- `services/company-user-management-service/.../config/SecurityConfig.java:189-217` -- copy → shared; both chains (`.decoder(jwtDecoder())`) take the bean.
- `services/event-management-service/.../config/SecurityConfig.java:203-230` -- copy → shared; `security/JwtStompInterceptor.java:32` keeps injecting the bean.
- `services/partner-coordination-service/.../config/SecurityConfig.java:155-160` -- Cognito-only → shared with default {COGNITO, AGENT}.
- Slice tests (load only SecurityConfig + probe controller, staging profile, real decoder): `api-gateway/src/test/.../config/ProductionSecurityFilterChainTest.java`, `services/company-user-management-service/src/test/.../config/ProductionSecurityFilterChainTest.java`, `services/event-management-service/src/test/java/ch/batbern/securitychain/EmsProductionSecurityFilterChainTest.java` (stays outside `ch.batbern.events`); PCS has none yet.
- `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/AbstractIntegrationTest.java:94-98` -- `@DynamicPropertySource`, add the agent test public key.
- `services/event-management-service/src/test/.../security/JwtStompInterceptorTest.java` -- mocks `JwtDecoder`; add a real-decoder case.
- Docs (doc-drift mappings for `shared-kernel/`, `api-gateway/`, `*/SecurityConfig.java`): `docs/architecture/06-backend-architecture.md:106-111,331`, `docs/architecture/08-operations-security.md:23`.

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/security/TestTokens.java` -- per-JVM generated RSA-2048 test key pair; `agentPublicKeyPem()`, `agentToken(sub, username, run, principal)`, a variant builder (claims customizer, other signing key, HS256), `watchToken(secret, subject, role)`; class Javadoc: TEST-ONLY, generated at runtime. Add `testFixturesImplementation` for oauth2-resource-server/nimbus if the compile needs it -- one source of test tokens for all services.
- [x] `shared-kernel/src/test/java/ch/batbern/shared/security/BatbernJwtDecoderTest.java` -- RED first: every row of the I/O matrix (Cognito routed to a local JWK set or a stub verifier injected through a package-private builder; no network) -- proves routing and refusals.
- [x] `shared-kernel/src/main/java/ch/batbern/shared/security/{TokenKind,BatbernJwtDecoder,AgentTokenValidator}.java` -- implement; iss read with `JWTParser`; per-kind verifiers as specified; Javadoc names the properties -- GREEN.
- [x] `api-gateway/.../config/SecurityConfig.java`, `CognitoConfig.java`, dead auth classes/tests, `build.gradle` -- adopt and delete as in Code Map.
- [x] CUMS, EMS, PCS `SecurityConfig.java` -- adopt; chains take the injected `JwtDecoder`.
- [x] Gateway, CUMS, EMS slice tests -- add (existing tests untouched): agent token reaches the probe (200); agent token signed with another key → 401; `@DynamicPropertySource` for `agent.jwt.public-key`.
- [x] `services/partner-coordination-service/src/test/java/ch/batbern/partners/config/PcsProductionSecurityFilterChainTest.java` -- new slice test: agent token → 200 on an authenticated-only path, Watch token (valid HS256 with the configured secret) → 401, anonymous → 401, public `GET /api/v1/partners` → 200 control.
- [x] `JwtStompInterceptorTest` -- one case with the real shared decoder: agent CONNECT accepted, other-key agent CONNECT rejected.
- [x] `AbstractIntegrationTest` -- register `agent.jwt.public-key` from `TestTokens` so every IT context runs with the agent kind on, as production will.
- [x] Docs 06 and 08 -- name the shared decoder and the per-service kinds.

**Acceptance Criteria:**
- Given each service's existing production-chain tests and CUMS `WatchPairingIntegrationTest`, when the suites run, then they pass without edits to those test methods.
- Given a test-key agent token, when it is sent to gateway, CUMS, EMS and PCS chains, then each lets it through; given a valid Watch token, PCS answers 401 while gateway/CUMS/EMS accept it.
- Given the code base, when grepping `services/` and `api-gateway/src/main` for `isWatchJwt` or `NimbusJwtDecoder`, then there are no hits outside shared-kernel.

## Implementation Notes

- `BatbernJwtDecoder.fromEnvironment` binds `batbern.security.token-kinds` with Spring Boot's `Binder` (comma string or YAML list; an unknown kind fails startup), not a plain `getProperty`, so a YAML list cannot silently fall back to the default.
- Cognito verifier: jwk-set-uri first, then issuer-uri, else `IllegalStateException` -- for all four services. CUMS/EMS/PCS used to throw `IllegalArgumentException` without jwk-set-uri and ignore issuer-uri; all three set jwk-set-uri in every profile, so the effective choice is unchanged. `withIssuerLocation(...).build()` resolves the discovery document eagerly (measured: unit test hit the network), same as the gateway before; that case therefore has no unit test.
- Spring's `MappedJwtClaimSetConverter` invents `iat = exp - 1 s` when `iat` is missing (verified in the 7.0.8 bytecode). The agent verifier drops an invented `iat` so `AgentTokenValidator` can refuse it.
- `TestTokens` lives in shared-kernel test fixtures; `testFixturesImplementation 'org.springframework.security:spring-security-oauth2-jose'` brings Nimbus (no BOM entry for `com.nimbusds` itself).
- Gateway: `ConfigController` still reads `aws.cognito.userPoolClientId`, so the yml property stays; only the `CognitoConfig` field went. Dependabot `com.auth0:*` group pattern removed with the dependencies. ADR-008 diagram line updated.
- EMS chain keeps picking the decoder bean implicitly (it never called `.decoder(...)`); gateway, CUMS, PCS chains take it as a parameter.

## Plan Change Log

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 3 findings — high 1, medium 0, low 1, false 1, maybe-false 0
- findings:
  - `[high]` `[patch]` Agent lifetime checked only as exp - iat; a future iat (now+1y, exp=iat+300 s) or iat after exp passed, giving a long-lived bearer token in all four services — fixed: `AgentTokenValidator` refuses iat after exp and iat later than now + 60 s (Clock, default systemUTC); tests `should_refuse_when_iatInTheFuture`, `should_refuse_when_iatAfterExp`.
  - `[low]` `[patch]` `TestTokens` redefined `AGENT_ISSUER`/`WATCH_ISSUER` already public on `BatbernJwtDecoder` — fixed: fixture fields removed (no caller), fixture uses the decoder constants.
  - `[false]` `[reject]` PCS Watch-refusal test also sends an agent token first — that is the positive control coding-standards.md "Assertion rules" requires for an absence assertion; keep.

## Design Notes

Architecture note: modules touched -- shared-kernel security, gateway config, CUMS/EMS/PCS config (L0 platform). New dependencies -- none cross-service; services already depend on shared-kernel. New endpoints -- none. Reused -- Spring `NimbusJwtDecoder`, `JwtValidators`, `JwtRolesConverter` (agent roles come from `role_assignments` by sub, C1). Deviations -- none; shared code moves into shared-kernel as the map requires.

Routing by iss with verification by the chosen verifier keeps today's guarantee (a forged iss only picks a verifier that then fails). Parsing iss properly replaces the `contains("\"iss\":\"batbern-watch\"")` sniff, which mis-routed any token serialised with whitespace. Refusing a disabled kind outright (instead of falling through to Cognito) makes "PCS refuses Watch" an explicit rule, not a side effect. The agent validator rejects role claims so a token can never carry its own roles (AD-3).

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-1-shared-kernel.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :api-gateway:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-1-gateway.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-1-cums.log` -- expected: BUILD SUCCESSFUL (incl. WatchPairingIntegrationTest, ProductionChainIntegrationTest)
- `set -o pipefail; ./gradlew :services:partner-coordination-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-1-pcs.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-1-ems.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew checkstyleMain checkstyleTest --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-1-checkstyle.log` -- expected: BUILD SUCCESSFUL
- `make check-test-ratchet` -- expected: no RAISED line

## Auto Run Result

**Summary:** One `JwtDecoder` in shared-kernel (`BatbernJwtDecoder`, `TokenKind`, `AgentTokenValidator`) routes by parsed `iss` to Cognito, Watch or agent verifiers, kinds per service (gateway/CUMS/EMS: Cognito+Watch+agent; PCS: Cognito+agent), `batbern.security.token-kinds` overrides, agent off without `agent.jwt.public-key`. Adopted in gateway, CUMS, EMS (STOMP interceptor takes the same bean) and PCS; three copies, the gateway's stray Cognito-only decoder and the dead auth0 validator chain removed.

**Files:** shared-kernel security (3 new classes + `BatbernJwtDecoderTest`), test fixture `TestTokens`, `AbstractIntegrationTest` (agent test key for every IT), `shared-kernel/build.gradle`; gateway `SecurityConfig`, `CognitoConfig`, 6 dead auth classes + 3 tests deleted, `build.gradle` and `.github/dependabot.yml` (auth0 gone); CUMS/EMS/PCS `SecurityConfig`; slice tests in gateway/CUMS/EMS, new `PcsProductionSecurityFilterChainTest`, `JwtStompInterceptorTest`; docs 06, 08, ADR-008.

**Review:** quick lens; 2 patches (1 high, 1 low), 0 deferred, 1 rejected (false: required positive control).

**Follow-up review recommended:** true. One high was patched (iat checks); the specific unverified risk is that the patch itself has had only the lens-free self-check, not a second independent review of the validator time rules.

**Verification:** before review, full suites: shared-kernel 752, gateway 333 (26 skipped), CUMS 1083 (15 skipped), EMS 3140 (6 skipped), PCS 310, all 0 failures; checkstyle clean; `make check-test-ratchet` no RAISED. After the patch (shared-kernel only): shared-kernel full 754/0 failures + checkstyle, gateway full 333/0, and the token-touching tests in CUMS (51: chain slice + WatchPairing), PCS (8), EMS (87: chain slice, ProductionChainIntegrationTest, JwtStompInterceptorTest); ratchet clean. Full CUMS/EMS/PCS suites were not re-run after the patch (the patch only tightens agent-token time checks; only those tests use agent tokens).

**Residual risks:** no run against local dev or staging; Cognito verification against the live JWK endpoint and the gateway's issuer-uri discovery have no test (same code path as before). STOMP principal is the token's sub, which for an agent token is the Cognito sub, not the username (story 2's resolver).
