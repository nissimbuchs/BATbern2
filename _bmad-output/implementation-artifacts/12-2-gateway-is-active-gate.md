# Story 12.2: API-Gateway `is_active` Gate (SSO PR 1 — Part A)

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform engineer hardening the auth surface ahead of Google SSO federation**,
I want **a request-time `is_active` gate at the API Gateway — an `OncePerRequestFilter` that runs after JWT authentication, resolves the caller's account status from CUMS (Caffeine-cached ~60s), and returns `403 ACCOUNT_DEACTIVATED` for a deactivated account while failing open on a CUMS error and being killable via a feature flag**,
so that **deactivation becomes effective within ~60s instead of ≤24h (the token lifetime), and — crucially — federated (Google) logins are gated too, since the `PreAuthentication` Lambda that is the only `is_active` enforcement today never fires for external-IdP sign-ins.**

This is **Part A of PR 1** of Epic 12 (SSO / OIDC Federation). It is **backend-only and invisible to active users**. It moves the `is_active` guarantee off the Cognito `PreAuthentication` trigger (which Cognito skips for federated logins, and which only bites at login) onto a provider-agnostic, request-time path at the single front door (the gateway). This makes SSO **Phase 3 verify-only** for the inactive-block half. Source: `docs/plans/sso-oidc-federation.md` §5 "PR 1 … Part A — `is_active` gate at the API Gateway"; ADR context: `docs/architecture/06b-user-lifecycle-sync.md` §"⚠️ PreAuthentication Trigger — CORRECTED" (lines 743-760). **Part B** (canonical JIT — `JITUserProvisioningInterceptor` reading names from `custom:preferences`) is **Story 12.3** and is explicitly out of scope here.

## Acceptance Criteria

1. **(New gateway filter, runs after JWT auth, authenticated-only.)** A new `OncePerRequestFilter` (e.g. `ch.batbern.gateway.security.AccountActiveFilter`) is added to `api-gateway`. It reads the authenticated principal from the Spring Security context exactly as `RateLimitingFilter.getUserContext` already does (`api-gateway/.../security/RateLimitingFilter.java:185-205`: `SecurityContextHolder.getContext().getAuthentication()`, then `auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt`). It **does nothing and calls `chain.doFilter` immediately** when the request is unauthenticated/anonymous — i.e. `auth == null`, `!auth.isAuthenticated()`, `"anonymousUser".equals(auth.getPrincipal())`, or the principal is not a `Jwt`. This guarantees the public/`permitAll` routes in `SecurityConfig.defaultSecurityFilterChain` (`api-gateway/.../config/SecurityConfig.java:176-291`, e.g. `/api/v1/events/current`, `/api/v1/config`, anonymous registration) are never gated. Filter ordering: it must run **after** Spring Security has authenticated the request — model the precedence on `RateLimitingFilter` which uses `@Order(Ordered.LOWEST_PRECEDENCE)` with the comment "After Spring Security authentication" (`RateLimitingFilter.java:33`).

2. **(Resolve username from the JWT — `custom:username`, fallback `sub`.)** The gate keys on the meaningful username injected by the PreTokenGeneration Lambda: read `jwt.getClaimAsString("custom:username")`; if null/empty, fall back to `jwt.getSubject()` (the Cognito `sub`). This mirrors `UserContextExtractor.extractUserContext` (`api-gateway/.../auth/UserContextExtractor.java:31-37` prefers `custom:username`) and the JIT path (`JITUserProvisioningInterceptor.java:82` uses `jwt.getSubject()`). Note that `getUserByUsername` (AC3) expects the **username** per ADR-003; when only `sub` is available (e.g. a token issued before username projection) the lookup may 404 → treat as fail-open (AC5).

3. **(Resolve account status via CUMS `getUserByUsername` → `UserResponse.active`.)** The filter looks up the caller's status by calling the company-user-management-service (CUMS) and reading the `active` flag. **Grounding/discrepancy (verified 2026-06-01):** the plan claims an *existing* `UserServiceClient.getUserByUsername` in `api-gateway` — **this does not exist**; `api-gateway` has no user client (`grep -rln "UserServiceClient\|getUserByUsername" api-gateway/src/main/java` → no matches). The reference implementation that DOES exist is `services/partner-coordination-service/.../client/UserServiceClient.java:22` (`UserResponse getUserByUsername(String username)`) backed by `UserServiceClientImpl`, whose `UserResponse.active` is the generated `Boolean active` field (`services/partner-coordination-service/build/generated-client-user/.../UserResponse.java:130,402`), populated server-side by `UserResponseMapper` (`services/company-user-management-service/.../service/UserResponseMapper.java:54`: `.active(user.isActive())`). **Therefore this story must ADD a minimal user-status client to `api-gateway`** (call `GET {company-user-management.url}/api/v1/users/{username}` and read `active`), reusing the existing shared `RestTemplate` bean (`api-gateway/.../config/WebClientConfig.java:25-31`) and the CUMS URL property already defined in `DomainRouter` (`api-gateway/.../routing/DomainRouter.java:45-46`, `services.company-user-management.url`). The caller's `Authorization` header (the same JWT) must be propagated on this internal call so CUMS authorizes the `/users/{username}` read.

4. **(Caffeine cache, key = username/sub, TTL ≈ 60s, configurable.)** The status lookup is cached in a **Caffeine** cache keyed on the resolved username/sub, with `expireAfterWrite` ≈ 60s, so CUMS is hit at most once per user per minute (keeps CUMS off the hot path; cuts deactivation lag from ≤24h → ≤60s). TTL is configurable via `security.active-gate.ttl-seconds` (default 60). **Grounding/discrepancy:** Caffeine is the project cache standard (no Redis) but is **NOT currently a dependency of `api-gateway`** (`api-gateway/build.gradle` has no caffeine line) — it must be ADDED (`implementation 'com.github.ben-manes.caffeine:caffeine:3.2.3'`, the version already used by CUMS at `services/company-user-management-service/build.gradle:27`). Model the cache construction on `services/company-user-management-service/.../config/CacheConfig.java:48-53` (`Caffeine.newBuilder().expireAfterWrite(...).maximumSize(...).recordStats()`). A direct `com.github.benmanes.caffeine.cache.Cache<String,Boolean>` is sufficient (no need for Spring `@Cacheable`/`CacheManager`).

5. **(Deactivated → `403` + error code `ACCOUNT_DEACTIVATED`, NOT 401.)** When the resolved status is `active == false`, the filter short-circuits the chain and writes a `403 Forbidden` JSON body carrying a machine-readable error code `ACCOUNT_DEACTIVATED` (e.g. `{"error":"ACCOUNT_DEACTIVATED","message":"Your account has been deactivated."}`), `Content-Type: application/json`, and does **not** call `chain.doFilter`. **It MUST NOT return 401** — a 401 would trigger the frontend's JWT refresh loop (`_bmad-output/project-context.md:280-281`: "Never call `refreshJWT()` on 401 … triggers infinite auth retry (401 → refresh → onChange → sync → 401 → …)"). 403 is terminal for the SPA. CORS headers must be attached to the 403 the same way `RateLimitingFilter` attaches them to its 429 (`RateLimitingFilter.java` `addCorsHeaders`), so the browser can read the error code cross-origin.

6. **(Fail-open on CUMS error.)** If the CUMS lookup throws (timeout, 5xx, connection error) the filter **allows the request** (calls `chain.doFilter`), logs a WARN, and emits a metric (e.g. micrometer counter `gateway.active_gate.cums_error`). This matches the existing graceful-degradation contract: PreTokenGen → empty roles, `pre-authentication.ts:103-115` → allow on DB error, `JITUserProvisioningInterceptor.java:163-169` → allow on any exception. A `404` from CUMS (user not yet provisioned — e.g. fresh federated identity, or a `sub`-only lookup) is **also fail-open** (allow), consistent with `pre-authentication.ts:55-65` ("User not found — allow; JIT provisioning will handle"). The gate only ever *tightens* over today's no-gate state.

7. **(Kill-switch `security.active-gate.enabled`.)** A boolean property `security.active-gate.enabled` (default — see Open Questions) controls the filter. When `false`, the filter is a pure pass-through (calls `chain.doFilter` immediately, no CUMS call, no cache). This lets the gate ship dark, be verified with a single deliberate test deactivation, then be flipped on — and lets a bug be reverted instantly without a redeploy. The flag is read from configuration (e.g. `@Value("${security.active-gate.enabled:false}")`).

8. **(Cache invalidation = TTL only; active eviction is out of scope.)** Invalidation is TTL-based only — a ≤60s deactivation lag is accepted for this rare event. An active eviction path (EventBridge `UserDeactivated` event evicting the cache key) is documented as a **FUTURE/out-of-scope** option in the Dev Notes, NOT built here.

9. **(Tests — TDD.)** Unit tests for the filter cover: (a) **active → pass** (chain proceeds, 200/forwarded); (b) **inactive → 403** with body containing `ACCOUNT_DEACTIVATED` and chain NOT invoked; (c) **cache-hit path** — two requests for the same user trigger the CUMS lookup only once; (d) **CUMS error → fail-open** (chain proceeds, WARN logged); (e) **CUMS 404 → fail-open**; (f) **unauthenticated/anonymous → pass-through, no CUMS call**; (g) **`security.active-gate.enabled=false` → pure pass-through, no CUMS call**. Plus a **gateway integration test** (`@SpringBootTest`, MockMvc/`WebTestClient`) asserting that with a deactivated user the gateway returns `403 ACCOUNT_DEACTIVATED` and with an active user the request is forwarded. Test naming `should_expectedBehavior_when_condition` per project standard. Integration tests that touch a DB extend `AbstractIntegrationTest` (PostgreSQL Testcontainers) — but the gateway has no DB, so CUMS is stubbed (the gateway integration test mocks/stubs the user-status client rather than standing up CUMS).

10. **(Doc-drift, same commit — `feat` touching auth, NOT `[no-doc]`.)** `docs/architecture/06b-user-lifecycle-sync.md` §"⚠️ PreAuthentication Trigger — CORRECTED" (lines 743-760) is updated: the **Target (ADR-010)** paragraph (lines 757-760), currently future-tense ("replace PreAuthentication with a request-time `is_active` gate … then retire the PreAuthentication trigger"), is rewritten to **present-tense done** for Part A — the API-Gateway `is_active` gate is now the **canonical** enforcement (Caffeine ~60s, `403 ACCOUNT_DEACTIVATED`, fail-open, kill-switch `security.active-gate.enabled`), provider-agnostic so it covers federated logins and the post-issuance window; `PreAuthentication` is now **redundant** and slated for retirement in the cleanup track (after the gate is confirmed live in prod). **Doc-drift mapping discrepancy (verified 2026-06-01):** `.github/doc-drift-mappings.yml:54-57` maps the `api-gateway/` source pattern to `06-backend-architecture.md` + `docs/api/` — it does **NOT** map api-gateway to `06b-user-lifecycle-sync.md` (06b is only reachable via the CUMS source pattern, `:30-32`). So the auto-auditor will not flag 06b for an api-gateway-only change; the 06b edit is done here **deliberately** because that is where the now-stale "Target" wording lives. (Optional: add `06b-user-lifecycle-sync.md` to the `api-gateway/` mapping block — see Open Questions.)

## Tasks / Subtasks

- [x] **Task 1 — Add Caffeine dependency + filter skeleton (AC: 1, 4, 7)**
  - [x] RED: write `AccountActiveFilterTest` asserting (f) unauthenticated/anonymous request → `chain.doFilter` called, no status lookup; (g) `security.active-gate.enabled=false` → pass-through, no status lookup. (Tests fail — class doesn't exist yet.)
  - [x] GREEN: add `implementation 'com.github.ben-manes.caffeine:caffeine:3.2.3'` to `api-gateway/build.gradle` (match CUMS version `services/company-user-management-service/build.gradle:27`).
  - [x] GREEN: create `ch.batbern.gateway.security.AccountActiveFilter extends OncePerRequestFilter`. Read the principal via `SecurityContextHolder` exactly like `RateLimitingFilter.getUserContext` (`RateLimitingFilter.java:185-205`); pass through on anonymous/non-JWT/disabled-flag. Inject `@Value("${security.active-gate.enabled:false}")` and `@Value("${security.active-gate.ttl-seconds:60}")`. Register a `Caffeine.newBuilder().expireAfterWrite(ttl, SECONDS).maximumSize(...).recordStats()` cache (`Cache<String,Boolean>`), modelled on `CacheConfig.java:48-53`.
  - [x] Ensure ordering runs AFTER Spring Security auth (mirror `RateLimitingFilter`'s `@Order(Ordered.LOWEST_PRECEDENCE)` rationale; if registered as a `@Component OncePerRequestFilter`, confirm via integration test it sees the authenticated `Jwt`).

- [x] **Task 2 — Username resolution + minimal CUMS user-status client (AC: 2, 3, 6)**
  - [x] RED: extend `AccountActiveFilterTest` — (a) active user → pass; (b) inactive → 403; (d) CUMS exception → fail-open (pass + WARN); (e) CUMS 404 → fail-open. Stub the new status client.
  - [x] GREEN: resolve username = `jwt.getClaimAsString("custom:username")` else `jwt.getSubject()` (mirror `UserContextExtractor.java:31-37`).
  - [x] GREEN: add a minimal `GatewayUserStatusClient` in `api-gateway` (NOTE: no existing client — see AC3 discrepancy). Call `GET {services.company-user-management.url}/api/v1/users/{username}` using the shared `RestTemplate` bean (`WebClientConfig.java:25-31`) and the URL property used by `DomainRouter.java:45-46`. Propagate the caller's `Authorization` header. Parse the `active` field (model the contract on `UserResponse.active`, `UserResponseMapper.java:54`). On any exception OR 404 → return "unknown/allow" so the filter fails open.
  - [x] GREEN: cache `active` per username with the Caffeine cache (key = username/sub).

- [x] **Task 3 — 403 ACCOUNT_DEACTIVATED short-circuit (AC: 5)**
  - [x] RED: assert the inactive-user response is HTTP **403** (not 401), `Content-Type: application/json`, body contains `"ACCOUNT_DEACTIVATED"`, and `chain.doFilter` is NOT called.
  - [x] GREEN: when `active == false`, write the 403 JSON body + attach CORS headers (reuse the `RateLimitingFilter` `addCorsHeaders` approach) and return without proceeding.
  - [x] Confirm 401 is never produced by this filter (regression guard against the refresh-loop trap, `project-context.md:280-281`).

- [x] **Task 4 — Cache-hit + metric (AC: 4, 6, 9c)**
  - [x] RED: assert two requests for the same user invoke the CUMS client only once (cache hit).
  - [x] GREEN: confirm cache wiring; add a micrometer counter for `cums_error` (and optionally `account_deactivated_blocked`) so fail-open events are observable.

- [x] **Task 5 — Gateway integration test (AC: 9)**
  - [x] RED→GREEN: `@SpringBootTest` (MockMvc/`WebTestClient`) — with the status client stubbed to `active=false`, a request to a protected route returns `403 ACCOUNT_DEACTIVATED`; with `active=true` it forwards (or reaches the next stub). Stub the user-status client (gateway has no DB → no Testcontainers needed here).

- [x] **Task 6 — Docs same commit (AC: 10)**
  - [x] Rewrite `06b-user-lifecycle-sync.md:757-760` "Target (ADR-010)" paragraph to present-tense done for the gateway gate (canonical enforcement; PreAuthentication redundant, retirement deferred to cleanup track). Keep the cross-references to `ADR-010-…` and `docs/plans/sso-oidc-federation.md`.
  - [x] (Optional, per OQ-2) add `docs/architecture/06b-user-lifecycle-sync.md` to the `api-gateway/` block in `.github/doc-drift-mappings.yml:54-57`.

- [x] **Task 7 — Full verification + deploy/kill-switch note**
  - [x] `./gradlew :api-gateway:test` (output via `tee` to a temp file, then grep — per CLAUDE.md), all green; `make verify` or targeted lint.
  - [x] PR description records: deploy `security.active-gate.enabled=false` first → verify the filter is inert → perform one deliberate test deactivation → flip `enabled=true` → confirm `403 ACCOUNT_DEACTIVATED` and that active users are unaffected. Rollback = set `enabled=false` (instant) or revert.

## Dev Notes

### Architecture context — why this is the right seam
- **The gateway is the single front door.** All external `/api/v1/**` traffic is proxied by `ProxyController` → `DomainRouter` (`api-gateway/.../routing/ProxyController.java:29-47`, `DomainRouter.java`). Spring Security's `oauth2ResourceServer().jwt(...)` (`SecurityConfig.java:292`) authenticates the request before the controller runs, so a `OncePerRequestFilter` placed after authentication sees a populated `Jwt` principal and can gate every authenticated call in one place — without touching any downstream service.
- **`PreAuthentication` is the ONLY `is_active` enforcement today (RED-PHASE — see below).** This story does not weaken it; it adds a stronger, provider-agnostic gate. Federated logins (Google, once SSO ships) never invoke `PreAuthentication` (Cognito fires only Pre-Sign-up / Pre-Token-Generation / Post-Authentication for external IdPs — `docs/plans/sso-oidc-federation.md` §3), so without this gate a deactivated user could sign in via Google unchecked. The gateway gate closes that and also the post-issuance window (a deactivated user's still-valid 24h token stops working within ~60s).
- **Fail-open is intentional and consistent.** Every existing `is_active`/role path degrades open on infra error: `pre-authentication.ts:103-115`, `JITUserProvisioningInterceptor.java:163-169`, PreTokenGen→empty roles. Failing closed here would let a CUMS hiccup lock out the entire platform — strictly worse than today's no-gate behaviour.

### RED-PHASE verification (the plan's load-bearing claim — CONFIRMED 2026-06-01)
The sprint-status one-liner asks to verify "PreAuthentication is the ONLY `is_active` gate today." **Confirmed against source:**
- **`pre-authentication.ts` IS the only enforcement.** `infrastructure/lib/lambda/triggers/pre-authentication.ts:48-84` queries `SELECT is_active … FROM user_profiles WHERE cognito_user_id = $1` and `callback(errorMessage)` + `throw` when `!is_active` (lines 70-84). Not-found → allow (55-65); DB error → allow (103-115). This is the documented sole gate.
- **`JITUserProvisioningInterceptor` only SETS `isActive(true)` on create — it never CHECKS an existing user.** `services/company-user-management-service/.../interceptor/JITUserProvisioningInterceptor.java:148` sets `.isActive(true)` only on the new-user `User.builder()`; the early-return for an existing user (`findByCognitoUserId(...).isPresent()` → `return true`, line 89-91) and the email-link branch (118-128) read **no** `is_active`. No request-time gate reads `is_active` anywhere in `api-gateway` (`SecurityConfig` does JWT/role only).
- **06b already self-corrected (2026-05-31).** `docs/architecture/06b-user-lifecycle-sync.md:743-760` already replaced the old false "app logic checks `is_active`" claim with the corrected account + an ADR-010 "Target". So the plan's instruction to "rewrite the false 06b line" is **partly already done** — this story flips the remaining future-tense **Target** paragraph (757-760) to present-tense done for Part A. (The story does NOT need to re-correct the already-fixed false claim.)

### Plan-vs-reality discrepancies (resolve in implementation, do not silently invent)
1. **No `UserServiceClient` in `api-gateway`.** The plan says to use "the existing `UserServiceClient` (`getUserByUsername`)" in the gateway — it does not exist there. The real reference is `partner-coordination-service`'s `UserServiceClient`/`UserServiceClientImpl`. **Action:** add a minimal user-status client to `api-gateway` reusing the shared `RestTemplate` (`WebClientConfig.java:25-31`) and the CUMS URL (`DomainRouter.java:45-46`). (See AC3.)
2. **Caffeine is not yet an `api-gateway` dependency.** It is the project standard (CUMS `build.gradle:27` `3.2.3`) but absent from `api-gateway/build.gradle`. **Action:** add it. (See AC4.)
3. **`UserResponseMapper:52`** — the plan cites line 52; the actual `.active(user.isActive())` mapping is at `UserResponseMapper.java:54` (the `new UserResponse()` builder block begins ~line 40). The `active` field itself is real and populated. Minor line drift only.
4. **Doc-drift mapping does not cover api-gateway → 06b.** `.github/doc-drift-mappings.yml:54-57` (api-gateway) lists `06-backend-architecture.md` + `docs/api/`, not 06b. The 06b edit is deliberate (that's where the stale wording is); optionally extend the mapping (OQ-2).

### Files to touch — current state & what to preserve
| File | Current state | Change | Preserve |
|---|---|---|---|
| `api-gateway/build.gradle` | no caffeine dep (`:29-33` starters only) | add `com.github.ben-manes.caffeine:caffeine:3.2.3` | all existing deps |
| `api-gateway/.../security/AccountActiveFilter.java` | does not exist | NEW `OncePerRequestFilter` — the gate | n/a |
| `api-gateway/.../client/GatewayUserStatusClient.java` (or similar) | does not exist | NEW minimal CUMS user-status lookup (`active`) | n/a |
| `api-gateway/.../config/WebClientConfig.java` | `:25-31` shared `RestTemplate` bean | reuse (no change, or add a tighter-timeout bean if desired) | the shared bean + turnstile bean |
| `api-gateway/.../routing/DomainRouter.java` | `:45-46` `services.company-user-management.url` property | reuse the same property for the status client | routing logic |
| `api-gateway/.../security/RateLimitingFilter.java` | `:185-205` JWT-from-SecurityContext pattern; `addCorsHeaders` | **model only** (read pattern + CORS-on-error) | unchanged |
| `api-gateway/.../config/SecurityConfig.java` | `:176-291` permitAll list; `:292` oauth2 jwt | **no change** — the filter self-skips anonymous/permitAll | filter chain |
| `docs/architecture/06b-user-lifecycle-sync.md` | `:757-760` future-tense "Target (ADR-010)" | flip to present-tense done (Part A) | the corrected `:743-756` block |
| `.github/doc-drift-mappings.yml` | `:54-57` api-gateway → 06-backend + docs/api | optional: add 06b (OQ-2) | other mappings |

### Application config (where the new properties live)
- `security.active-gate.enabled` (default `false` pending OQ-1) and `security.active-gate.ttl-seconds` (default `60`) go in `api-gateway/src/main/resources/application*.yml`/`.properties`. Read via `@Value` (no need for a `@ConfigurationProperties` class for two keys). Keep `enabled=false` in the committed default so the deploy ships dark (AC7).

### Testing standards (per CLAUDE.md 4-layer + TDD red-green-refactor)
- Filter unit tests: pure JUnit/Mockito — mock the user-status client + `FilterChain`, build a `Jwt` principal into a `SecurityContextHolder` test context, assert chain-invoked vs 403. Name `should_…_when_…`.
- Gateway integration test: `@SpringBootTest` with `@Profile("test")` security chain (`SecurityConfig.testSecurityFilterChain`, `:140-157`) — stub the status client; the gateway has no DB so **no Testcontainers** here (Testcontainers/`AbstractIntegrationTest` is for DB-backed services per CLAUDE.md).
- Run `./gradlew :api-gateway:test` and dump output via `tee` to a temp file, then grep (CLAUDE.md) — don't re-run repeatedly. Single class: `./gradlew :api-gateway:test --tests AccountActiveFilterTest`.

### Project Structure Notes
- New filter lives under `api-gateway/src/main/java/ch/batbern/gateway/security/` beside `RateLimitingFilter`, `SecurityHeadersFilter`, `TurnstileVerificationFilter`. The status client can live under a new `client/` package (gateway currently has none) or under `auth/`.
- Deploy tier (infra/CLAUDE.md): this is code-only in `api-gateway` (no infra/Dockerfile/migration change) → **fast-path** (`api-gateway` service deploy, 2-5 min). Risk low (invisible to active users; fail-open + kill-switch). Rollback: flip `security.active-gate.enabled=false` (instant, no redeploy if config-served) or revert the commit.

### References
- [Source: docs/plans/sso-oidc-federation.md#PR 1 — SSO-enabling backend … Part A] (lines 112-147, 157-162 — gate spec, fail-open, kill-switch, TTL, doc note)
- [Source: docs/plans/sso-oidc-federation.md#3 The two Cognito gotchas] (lines 42-55 — PreAuthentication not fired for federated)
- [Source: docs/plans/sso-oidc-federation.md#4 staging IS production] (lines 69-77 — single real pool; every change is prod)
- [Source: docs/architecture/06b-user-lifecycle-sync.md:743-760 — PreAuthentication CORRECTED + ADR-010 Target] (the doc-drift target)
- [Source: api-gateway/.../security/RateLimitingFilter.java:33,185-205 — post-auth ordering + JWT-from-SecurityContext + CORS-on-error pattern]
- [Source: api-gateway/.../auth/UserContextExtractor.java:31-37 — custom:username preferred over sub]
- [Source: api-gateway/.../config/SecurityConfig.java:176-291 — permitAll list; :292 oauth2 jwt; :140-157 test chain]
- [Source: api-gateway/.../routing/DomainRouter.java:45-46 — services.company-user-management.url] · [Source: api-gateway/.../config/WebClientConfig.java:25-31 — shared RestTemplate]
- [Source: services/partner-coordination-service/.../client/UserServiceClient.java:22 — getUserByUsername reference impl] · [Source: …/build/generated-client-user/.../UserResponse.java:130,402 — Boolean active]
- [Source: services/company-user-management-service/.../service/UserResponseMapper.java:54 — .active(user.isActive())]
- [Source: services/company-user-management-service/.../config/CacheConfig.java:48-53 — Caffeine builder model] · [Source: …/build.gradle:27 — caffeine 3.2.3]
- [Source: services/company-user-management-service/.../interceptor/JITUserProvisioningInterceptor.java:89-91,148,163-169 — only sets isActive(true), never checks; fail-open]
- [Source: infrastructure/lib/lambda/triggers/pre-authentication.ts:48-84,55-65,103-115 — sole is_active gate, allow-on-not-found, fail-open]
- [Source: _bmad-output/project-context.md:280-281 — never 401-refresh-loop; use 403] · [Source: .github/doc-drift-mappings.yml:30-32,54-57]
- ADR-010 (federated identity via Cognito), ADR-001 (Cognito-for-auth-only), ADR-003 (meaningful usernames)

### Open Questions
- **OQ-1 — initial default of `security.active-gate.enabled`.** Plan says "deploy `false`, verify with a test deactivation, then flip `true`." Recommend committing the default as **`false`** so the merge ships dark, and flipping to `true` via a follow-up config change after the prod verification (AC7). Confirm with Nissim whether the flip happens in this PR (config change) or a separate one.
- **OQ-2 — extend the doc-drift mapping?** Should `06b-user-lifecycle-sync.md` be added to the `api-gateway/` block in `.github/doc-drift-mappings.yml:54-57` so future api-gateway auth changes are auto-flagged against 06b? Low-cost; recommend yes. (Task 6 optional sub-step.)
- **OQ-3 — frontend `ACCOUNT_DEACTIVATED` handler is OUT OF SCOPE here.** Part A is gateway-only (`docs/plans/sso-oidc-federation.md:125`). The frontend mapping of the `403 ACCOUNT_DEACTIVATED` code → forced logout + "account deactivated" message is a **follow-up** (belongs with the SSO frontend phase / a dedicated FE story). Until then a deactivated user sees a generic 403; the backend behaviour (block within ~60s) is fully delivered by this story. Confirm whether to spin a small FE follow-up story now or fold it into Phase 4/5.
- **OQ-4 — username vs sub on the CUMS lookup.** `getUserByUsername` keys on the ADR-003 username; tokens always carry `custom:username` once PreTokenGen runs, but a `sub`-only fallback (AC2) would 404 → fail-open (AC6). Acceptable (degrades open, no security regression vs today). Confirm no token path issues a JWT without `custom:username` for an *active deactivatable* user (the PreTokenGen Lambda always projects it for DB-backed users).

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story, 2026-06-02.

### Debug Log References

- Filter unit tests: `/tmp/12-2-filter-test2.log` (7 passed). First run `/tmp/12-2-filter-test.log` red — root cause: `new JwtAuthenticationToken(jwt)` (single-arg) is UNauthenticated; switched to the 2-arg `(jwt, authorities)` constructor (matches Spring's `BearerTokenAuthenticationFilter`).
- Client unit tests: `/tmp/12-2-it.log` (7 passed).
- Integration test: `/tmp/12-2-it2.log` (2 passed) — switched from `@AutoConfigureMockMvc` full-stack (filter-ordering/auth ambiguity left the gate inert → both paths 200) to a deterministic standalone MockMvc driving the real filter in-thread after the SecurityContext is set.
- Full suite: `/tmp/12-2-full.log` (`:api-gateway:test` BUILD SUCCESSFUL, 0 failures — confirms the `@Component` filter wires in every `@SpringBootTest` context). Checkstyle: `/tmp/12-2-checkstyle2.log` clean.

### Completion Notes List

- **AC1 (filter after auth, authenticated-only):** `AccountActiveFilter extends OncePerRequestFilter`, `@Order(LOWEST_PRECEDENCE)` (after Spring Security, mirroring `RateLimitingFilter`). Reads the principal via `SecurityContextHolder`; passes straight through for `auth==null`, `!isAuthenticated()`, `"anonymousUser"`, or non-`Jwt` principal — so `permitAll` public routes are never gated.
- **AC2 (username resolution):** `jwt.getClaimAsString("custom:username")` → `jwt.getSubject()` fallback.
- **AC3 (CUMS status client):** NEW `GatewayUserStatusClient` (gateway had none — verified) calls `GET {services.company-user-management.url}/api/v1/users/{username}` via the shared `RestTemplate`, forwarding the caller's JWT as `Bearer`. Reads `active` via a minimal `UserStatusResponse` (`@JsonIgnoreProperties(ignoreUnknown=true)`).
- **AC4 (Caffeine ~60s):** added `com.github.ben-manes.caffeine:caffeine:3.2.3` to `api-gateway/build.gradle`; `Cache<String,Boolean>` `expireAfterWrite(ttl)` `maximumSize(10_000)` `recordStats()`. `security.active-gate.ttl-seconds` (default 60). Known true/false cached; unknown (404/no-body) NOT cached (retries).
- **AC5 (403 ACCOUNT_DEACTIVATED, not 401):** inactive → terminal `403` JSON `{"error":"ACCOUNT_DEACTIVATED",...}`, `application/json`, CORS headers attached (mirrors `RateLimitingFilter.addCorsHeaders`), chain NOT invoked. Never 401 (refresh-loop trap).
- **AC6 (fail-open):** CUMS exception → allow + WARN + `gateway.active_gate.cums_error` counter; 404/no-body → allow (no metric). `GatewayUserStatusException` separates transient errors (metric) from 404 (expected).
- **AC7 (kill-switch):** `security.active-gate.enabled` (`@Value` default `false`) → pure pass-through (no CUMS, no cache). Ships dark; env `SECURITY_ACTIVE_GATE_ENABLED=true` flips it without a code redeploy.
- **AC8 (TTL-only invalidation):** active eviction documented as FUTURE in 06b, not built.
- **AC9 (tests):** 7 filter unit tests (a–g) + 7 client unit tests + 2 integration tests, all green. Naming `should_…_when_…`.
- **AC10 (docs same commit):** `06b-user-lifecycle-sync.md` "Target (ADR-010)" flipped to present-tense done for Part A (gateway gate canonical; PreAuthentication redundant, retirement deferred to cleanup track). Added `06b` to the `api-gateway/` doc-drift mapping (OQ-2 = yes).
- **OQ-1 resolved:** committed default `enabled=false` (ships dark); the flip to `true` is a follow-up config change after a prod test-deactivation (NOT in this PR).
- **Out of scope (confirmed):** Part B canonical JIT = Story 12.3; frontend `ACCOUNT_DEACTIVATED` handler (OQ-3) = later FE phase; PreAuthentication retirement = cleanup track.

### File List

**API Gateway (main)**
- `api-gateway/build.gradle` (M — add caffeine 3.2.3)
- `api-gateway/src/main/java/ch/batbern/gateway/security/AccountActiveFilter.java` (NEW — the gate)
- `api-gateway/src/main/java/ch/batbern/gateway/client/GatewayUserStatusClient.java` (NEW — CUMS user-status lookup)
- `api-gateway/src/main/java/ch/batbern/gateway/client/UserStatusResponse.java` (NEW — minimal `active` DTO)
- `api-gateway/src/main/java/ch/batbern/gateway/client/GatewayUserStatusException.java` (NEW — transient-failure signal)
- `api-gateway/src/main/resources/application.yml` (M — `security.active-gate.{enabled,ttl-seconds}`)

**API Gateway (test)**
- `api-gateway/src/test/java/ch/batbern/gateway/security/AccountActiveFilterTest.java` (NEW — 7 unit tests)
- `api-gateway/src/test/java/ch/batbern/gateway/client/GatewayUserStatusClientTest.java` (NEW — 7 unit tests)
- `api-gateway/src/test/java/ch/batbern/gateway/integration/AccountActiveGateIntegrationTest.java` (NEW — 2 integration tests)

**Docs**
- `docs/architecture/06b-user-lifecycle-sync.md` (M — "Target (ADR-010)" → present-tense done, Part A)
- `.github/doc-drift-mappings.yml` (M — api-gateway → 06b)

**Sprint tracking**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (M — 12-2 ready-for-dev → in-progress → review)

### Change Log

| Date | Change |
|---|---|
| 2026-06-02 | Story 12.2 implemented (API-gateway is_active gate, SSO PR 1 Part A). NEW `AccountActiveFilter` (OncePerRequestFilter, after auth) + `GatewayUserStatusClient` (CUMS `active` lookup, JWT-forwarded) + Caffeine ~60s cache; deactivated → `403 ACCOUNT_DEACTIVATED` (never 401); fail-open on CUMS error/404; kill-switch `security.active-gate.enabled` (ships `false`/dark). 16 tests (7 filter + 7 client + 2 integration), `:api-gateway:test` BUILD SUCCESSFUL, checkstyle clean. Docs: 06b "Target" → done for Part A + doc-drift mapping. Status → review. |
