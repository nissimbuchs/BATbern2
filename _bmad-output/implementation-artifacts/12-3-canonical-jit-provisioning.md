# Story 12.3: Canonical JIT Provisioning — provider-agnostic reconcile path (SSO PR 1 — Part B)

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform engineer enabling Google SSO without a parallel federated-user create path**,
I want **`JITUserProvisioningInterceptor` to fully mirror `post-confirmation.ts` when it provisions a `user_profiles` row — capturing firstName/lastName AND `language` from `custom:preferences`, linking an existing anonymous-by-email record, and defaulting to ATTENDEE — so that any authenticated identity (native OR federated) self-provisions a *correct* row on its first authenticated request**,
so that **federated sign-ins (which Cognito never sends through PostConfirmation — see plan §3) provision through the exact same provider-agnostic path as everyone else, closing the residual `custom:preferences` attribute divergence behind the 2026-05-18 duplicate-without-names incident and making Story 12.8 (Phase 3 federated provisioning) verify-only.**

This is **PR 1 — Part B** of Epic 12 (SSO / OIDC Federation). Its sibling **Part A — the API-Gateway `is_active` gate — is Story 12.2 and is NOT covered here**; the two together form PR 1 ("SSO-enabling backend: app-side `is_active` gate + canonical JIT"). Source: `docs/plans/sso-oidc-federation.md` §5 "PR 1 — Part B" (lines 148-162); target spec: `docs/architecture/06b-user-lifecycle-sync.md` §"JIT (Just-In-Time) Provisioning Interceptor — Safety Net" (lines 687-699).

**Prereq:** none. **Risk:** low — backend-only and invisible to active users (an existing user always matches `findByCognitoUserId`/`findByEmail` and is never re-created).

## Pre-implementation reality check — the plan's "Background" is partially stale (READ FIRST)

The plan (lines 119-123) states JIT "only *sets* `isActive(true)` on create, it never checks an existing user" and that the `custom:preferences` divergence is open. **Tracing the current source (2026-05-31) shows three of the four behaviours the plan asks for are ALREADY present** — they were added by the 2026-05-18 duplicate-without-names fix and the email-link work, *after* the plan's background paragraph was written:

| Behaviour the plan wants in JIT | Current state in `JITUserProvisioningInterceptor.java` | Action for this story |
|---|---|---|
| Read firstName/lastName from `custom:preferences` (mirror post-confirmation) | ✅ **Already done** — `extractNamesFromPreferences()` (`:181-198`), invoked when standard `given_name`/`family_name` are absent (`:102-113`) | Keep; add a guard test |
| Email-link an existing anonymous-by-email record | ✅ **Already done** — `findByEmail` → link `cognitoUserId` (`:118-128`) | Keep; add the integration test the plan asks for |
| Default to ATTENDEE | ✅ **Already done** — `extractRolesFromAuthorities` (`:256-280`, ATTENDEE fallback at `:274-277`) | Keep; assert in integration test |
| Capture `language` from `custom:preferences` into the row | ❌ **NOT done** — `User.builder()` (`:141-149`) never sets `preferences`; `@PrePersist` (`User.java:209-217`) then builds a **default** `UserPreferences` (`language="de"`, `UserPreferences.java:30-32`). A Swiss-French / EN signup that reaches JIT (PostConfirmation failure, or **every federated user** once SSO ships) silently loses its chosen language. | **THIS is the real gap to close** |

So the binding scope of Part B narrows to: **(1) close the `language` divergence** (the one piece of `custom:preferences` JIT still drops vs. `post-confirmation.ts`), and **(2) lock the already-present create + email-link + ATTENDEE behaviour behind Testcontainers integration tests** so it is provably the canonical reconcile path for the federated identities arriving in Phase 3. The doc (`06b`) is updated to describe JIT as the provider-agnostic canonical path rather than a "safety net". If the dev finds any of rows 1-3 has regressed since this story was written, restore it as part of the same task and note the discrepancy.

## Acceptance Criteria

1. **(Language captured from `custom:preferences`, mirroring `post-confirmation.ts`.)** When `JITUserProvisioningInterceptor` creates a NEW `user_profiles` row (the `User.builder()` path at `JITUserProvisioningInterceptor.java:141-149`), it sets `preferences` to a `UserPreferences` whose `language` is taken from the `language` field of the `custom:preferences` JSON — exactly as `post-confirmation.ts:224` (`const language = preferences.language || 'de'`) feeds the `pref_language` column at `post-confirmation.ts:309/323`. When `custom:preferences` is absent/malformed/has no `language`, the row falls back to the embeddable default `"de"` (`UserPreferences.java:32`) — so behaviour for today's name-only signups is unchanged. The existing `extractNamesFromPreferences()` helper (`:181-198`) is extended (or a sibling `extractLanguageFromPreferences()` added) to surface `language` without changing the name-extraction contract.

2. **(Names already from `custom:preferences` — guard against regression.)** The already-present name extraction (`extractNamesFromPreferences`, `:181-198`, invoked at `:102-113`) is preserved: a JWT carrying only `custom:preferences` (no `given_name`/`family_name`) still produces a row with the correct first/last name and a `firstname.lastname` username (the 2026-05-18 incident contract). This is asserted at integration level (Testcontainers), not only unit level, so the create truly persists names.

3. **(Fresh identity → row created + default ATTENDEE + names + language — the canonical create.)** Integration test (`AbstractIntegrationTest`, real PostgreSQL): an authenticated JWT for a `sub`/`email` with **no** existing `user_profiles` row and **no** roles in the authority list, carrying `custom:preferences={"firstName":...,"lastName":...,"language":"fr"}`, results — after the interceptor runs on a real `/api/**` request (per `WebMvcConfig` path mapping, `/api/**` at `WebMvcConfig.java:25`) — in exactly one persisted `user_profiles` row with: `cognito_user_id == sub`, the captured first/last name, `username` = `firstname.lastname`, `is_active = true`, `pref_language = 'fr'`, and a single `role_assignments` row of `ATTENDEE`. A `UserCreatedEvent` with `source == "JIT_PROVISIONING"` is published.

4. **(Existing-anonymous-by-email → linked, not duplicated.)** Integration test: a pre-seeded `user_profiles` row with `cognito_user_id = NULL` and a given `email` (the "pre-invited / historical participant" shape, `06b:693`, `06b:831`) — when an authenticated JWT with the **same email** but a fresh `sub` hits `/api/**` — is **linked** (the existing row's `cognito_user_id` is set to the JWT `sub`) rather than duplicated. No second row is created; the row's existing username/roles are preserved; **no** `UserCreatedEvent` is published (matching the unit-level contract at `JITUserProvisioningInterceptorTest.java:271-272`). This proves federated link-on-first-request works for users who already exist in the DB (e.g. organizer-invited speakers, the demo's primary path).

5. **(Default-ATTENDEE & non-blocking contract unchanged.)** The role-default (`extractRolesFromAuthorities` ATTENDEE fallback, `:274-277`) and the non-blocking error contract (`preHandle` always returns `true`, never throws — `:163-169`) are unchanged. No new exception path is introduced by the language addition (malformed JSON already handled by the try/catch in `extractNamesFromPreferences`, `:194-197`; the language read must be equally swallow-safe).

6. **(Provider-agnostic — no SSO-specific create code.)** After this story, there is exactly **one** create path for any first-request identity: the JIT interceptor reading the JWT (whose claims Cognito populates identically for native and federated logins via the Phase-1 attribute mapping → `custom:preferences`). **No** federated-only branch, **no** `triggerSource` check, **no** new endpoint. This is what makes Story 12.8 (Phase 3) verify-only: it confirms a real Google identity provisions correctly through this path and asserts nothing new is built. (Cross-reference, do not implement: the `is_active` gate for federated logins is Story 12.2 / Part A.)

7. **(Reconciliation parity — note, not necessarily code.)** `UserReconciliationService.createMissingUser` (`UserReconciliationService.java:307-350`) already mirrors JIT for names (`extractNamesFromPreferences`, `:362-379`) but **also drops `language`** (its `User.builder()` at `:336-344` sets no `preferences`). For consistency the dev SHOULD apply the same `language`-capture fix there in the same commit (the two paths are explicitly kept in sync per the comment at `UserReconciliationService.java:357`). If deferred, it MUST be called out as a known residual divergence in the Dev Agent Record — do not silently leave the two paths inconsistent.

8. **(Doc-drift, same commit.)** `docs/architecture/06b-user-lifecycle-sync.md` is updated **in the same commit** (per CLAUDE.md doc-drift rule; this is a `feat` changing provisioning business logic, so `[no-doc]` does NOT apply): the §"JIT (Just-In-Time) Provisioning Interceptor — Safety Net" block (`06b:687-699`) is rewritten so JIT is described as the **canonical, provider-agnostic reconcile path** (create + email-link + default ATTENDEE + names/language from `custom:preferences`) for native AND federated first-requests — not merely a "safety net" behind PostConfirmation — and the `custom:preferences` "Read at runtime" note (`06b:619`, signup-seed) is reconciled with JIT now reading `language` from it on the create path. Satisfies the doc-drift mapping `services/company-user-management-service/` → `06b-user-lifecycle-sync.md` (`.github/doc-drift-mappings.yml:30-32`).

## Tasks / Subtasks

- [x] **Task 1 — JIT: capture `language` from `custom:preferences` on create (AC: 1, 2, 5)**
  - [x] RED: added `should_setPrefLanguageFromCustomPreferences_when_jitProvisioningUser` + `should_defaultPrefLanguageToDe_when_preferencesHasNoLanguage` + `should_failGracefully_when_languageReadFromMalformedPreferences` to `JITUserProvisioningInterceptorTest.java` (ArgumentCaptor<User> pattern).
  - [x] GREEN: added swallow-safe `extractLanguageFromPreferences(Jwt)` (modelled on `extractNamesFromPreferences`); `User.builder()` now conditionally sets `.preferences(UserPreferences.builder().language(lang).build())` when a non-empty language was parsed, else leaves `preferences` unset so `@PrePersist` supplies `"de"`. Mirrors `post-confirmation.ts` (`language || 'de'`).
  - [x] REFACTOR: name-extraction contract untouched; only the language surface added. No behaviour change for name-only JWTs (verified by `should_defaultPrefLanguageToDe`).

- [x] **Task 2 — Integration test: fresh identity → canonical create (AC: 3, 5, 6)** — Testcontainers, real PostgreSQL
  - [x] RED/GREEN: added `JITProvisioningIntegrationTest extends AbstractIntegrationTest` (`@Transactional`, `@Import(TestAwsConfig.class)`, `@RecordApplicationEvents`). Drives a real authenticated `GET /api/v1/users` via the `jwt()` request post-processor (NOT `@WithMockUser` — the interceptor only acts on a `JwtAuthenticationToken`) with names + `"language":"fr"` in `custom:preferences`, empty authorities. Asserts one row, `cognitoUserId==sub`, names, `username=marie.favre`, `isActive`, `preferences.language=="fr"`, single ATTENDEE.
  - [x] Asserts a `UserCreatedEvent` with `source=="JIT_PROVISIONING"` published exactly once (Spring `ApplicationEvents` via `@RecordApplicationEvents`). Added a sibling case proving a no-preferences JWT defaults `pref_language` to `"de"`.

- [x] **Task 3 — Integration test: existing-anonymous-by-email → linked (AC: 4)** — Testcontainers
  - [x] RED/GREEN: in the same `JITProvisioningIntegrationTest`, pre-seeds a `cognito_user_id=null` row (email + username `pre.invited` + SPEAKER), then drives a fresh-`sub` same-email request. Asserts the same row now has `cognitoUserId==freshSub`, username/role preserved, exactly one row for the email, and **zero** `UserCreatedEvent`s.

- [x] **Task 4 — Reconciliation parity for `language` (AC: 7)** — same-commit consistency
  - [x] Applied the identical fix to `UserReconciliationService.createMissingUser`: added `extractLanguageFromPreferences(UserType)` (mirrors JIT) + conditional `.preferences(...)` on the `User.builder()`. Added `should_setPrefLanguageFromCustomPreferences_when_creatingMissingUser` to `UserReconciliationServiceTest` (asserts `preferences.language=="fr"`). OQ-1 resolved (Nissim): included in this commit.

- [x] **Task 5 — Docs same commit (AC: 8)**
  - [x] Rewrote `06b-user-lifecycle-sync.md` §"JIT … Safety Net" → "Canonical, provider-agnostic reconcile path" (create + email-link + default ATTENDEE + names **and language** from `custom:preferences`; PostConfirmation reframed as the native fast-path, not superior; notes federated never fires PostConfirmation → Story 12.8 verify-only; ADR-010 referenced). Reconciled the `custom:preferences` inventory row (`:619`) to note the backend create-path read.
  - [x] Doc-drift mapping CUMS → 06b satisfied (`.github/doc-drift-mappings.yml`). No `[no-doc]`.

- [x] **Task 6 — Full verification + deploy note**
  - [x] `./gradlew :services:company-user-management-service:test` → BUILD SUCCESSFUL, 0 failures (`/tmp/12-3-full-cums.log`); targeted unit+integration green (`/tmp/12-3-targeted.log`); checkstyleMain/Test clean (`/tmp/12-3-checkstyle.log`).
  - [x] PR note recorded in Completion Notes: gateway + CUMS fast-path/hotswap, code-only (no migration — `pref_language` already exists), rollback = revert the commit (no flag).

## Dev Notes

### Architecture context — why Part B is small and safe
- **JIT already does most of what Part B asks.** The 2026-05-18 duplicate-without-names fix (`JITUserProvisioningInterceptor.java:93-113`, comment at `:96-101` names the victims `nikolay.borissov.2` / `elmar.boschung.2`) already made JIT read names from `custom:preferences`, and the email-link branch (`:118-128`) + ATTENDEE default (`:274-277`) already exist. **The only piece of `post-confirmation.ts` that JIT still drops is `language`** — `post-confirmation.ts` carries it through `parseUserPreferences` → `createUser` (`:222-224`) → both the link `UPDATE` (`:252`) and the INSERT (`:309/323`), but JIT's `User.builder()` (`:141-149`) sets no `preferences`, so `@PrePersist` (`User.java:209-217`) silently substitutes the embeddable default `language="de"` (`UserPreferences.java:30-32`). For a Swiss-French or English signup that reaches JIT — a PostConfirmation failure today, and **every federated user** once SSO ships — the chosen language is lost. Closing this is the substance of Part B.
- **Why federated users hit JIT, not PostConfirmation.** Per plan §3 (lines 42-56): for external-IdP sign-ins Cognito fires **only** Pre-Sign-up, Pre-Token-Generation, and Post-Authentication — **never PostConfirmation**. So `post-confirmation.ts` (the native create path) never runs for a brand-new Google user; their `user_profiles` row is created by the JIT interceptor on their first authenticated API request. Making JIT capture exactly what PostConfirmation captures means federated and native users converge on identical rows.
- **"Provider-agnostic" is real here.** JIT reads only the JWT (`sub`, `email`, `given_name`/`family_name`, `custom:preferences`) and Spring authorities — none of which encode the IdP. Phase 1's Google attribute mapping (`docs/plans/...md:182-188`) folds Google `name`/`given_name`/`family_name` into the same `custom:preferences` / standard claims JIT already reads. No federated branch is needed → Story 12.8 is verify-only.
- **Invisible to active users.** An existing user matches `findByCognitoUserId` (`:89-91`, early-return) or `findByEmail` (`:118-128`, link-not-create), so the create branch only ever runs for genuinely-new identities. No re-creation, no role churn, no token change.

### Files to touch — current state & what to preserve
| File | Current state | Change | Preserve |
|---|---|---|---|
| `services/company-user-management-service/.../interceptor/JITUserProvisioningInterceptor.java` | `:181-198` reads firstName/lastName from `custom:preferences`; `:141-149` `User.builder()` sets **no** `preferences` → default `"de"` via `@PrePersist` | Read `language` from `custom:preferences` (extend `extractNamesFromPreferences` or add sibling) + set `.preferences(UserPreferences.builder().language(lang).build())` on create when present | Name extraction (`:102-113`, `:181-198`); email-link (`:118-128`); ATTENDEE default (`:274-277`); non-blocking contract (`:163-169`); username generation (`:213-245`) |
| `services/company-user-management-service/.../domain/User.java` | `@PrePersist :209-217` builds default `UserPreferences` when null | none (consumer of the builder) | The null-default fallback (drives AC1's "absent → de") |
| `services/company-user-management-service/.../domain/UserPreferences.java` | `pref_language` `length=2`, default `"de"` (`:30-32`) | none | default `"de"` |
| `services/company-user-management-service/.../service/UserReconciliationService.java` | `createMissingUser :307-350` mirrors JIT names (`:362-379`) but drops `language` (`User.builder() :336-344`) | apply the same `language`-capture fix (AC7) | the rest of reconciliation; the in-sync-with-JIT comment `:357` |
| `services/company-user-management-service/.../config/WebMvcConfig.java` | registers JIT on `/api/**` (`:22-26`) | none (the integration test relies on this mapping) | path mapping |
| `.../test/.../interceptor/JITUserProvisioningInterceptorTest.java` | unit tests incl. preferences-name cases (`:325-394`) | add language unit tests | all existing cases |
| `services/company-user-management-service/.../integration/JITProvisioningIntegrationTest.java` | **does not exist** | NEW — fresh-identity create + anonymous-by-email link, Testcontainers | — |
| `docs/architecture/06b-user-lifecycle-sync.md` | `:687-699` "JIT … Safety Net"; `:619` `custom:preferences` inventory note | rewrite JIT as canonical provider-agnostic path; reconcile language note | inventory table structure; Story 12.1 entries |

### Testing standards (per CLAUDE.md 4-layer + TDD red-green-refactor)
- **Integration tests MUST use PostgreSQL via Testcontainers** by extending `ch.batbern.shared.test.AbstractIntegrationTest` — never H2/`@DataJpaTest`. Model the new test's setup on `UserProvisioningAndPatchIntegrationTest.java:1-55` (`@Transactional`, `@Import(TestAwsConfig.class)`, autowired `MockMvc`/`UserRepository`). The interceptor only runs against `/api/**` (`WebMvcConfig.java:25`), so the test must drive a real authenticated request through `MockMvc`, not call `preHandle` directly (that is what the unit test does).
- **Unit tests** continue in `JITUserProvisioningInterceptorTest.java` (Mockito, `ArgumentCaptor<User>` — the `:301-309` idiom) for the language read; the integration test proves the row actually persists.
- **Test naming:** `should_<expected>_when_<condition>` (project convention, `JITUserProvisioningInterceptorTest.java:49`).
- **Resilience:** assert against the `language` *code* (`"fr"`/`"de"`), not any translated string. No real outbound comms are involved (JIT is DB-only) — safe under the "staging IS prod" rule.
- Run via `./gradlew :services:company-user-management-service:test` from repo root, **tee to a temp file then grep** (CLAUDE.md) — do not re-run the suite repeatedly.

### Project Structure Notes
- Integration tests live in `services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/`; `AbstractIntegrationTest` is the shared-kernel singleton-PostgreSQL base (`ch.batbern.shared.test.AbstractIntegrationTest`).
- Deploy tier (infra/CLAUDE.md): this is a **code-only** change to one Java service (CUMS) — no infra, no Dockerfile, no `V*.sql` migration (`pref_language` already exists) → **fast-path / hotswap**. Gateway is only co-deployed because PR 1's Part A (Story 12.2) touches it; Part B alone touches only CUMS.

### References
- [Source: docs/plans/sso-oidc-federation.md#PR 1 — Part B] (lines 148-162: canonical JIT, integration tests, deploy/risk/rollback, "makes Phase 3 verify-only"); §3 lines 42-56 (PostConfirmation/PreAuthentication do NOT fire for federated); §5 Phase 3 lines 219-227 (verify-only).
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/interceptor/JITUserProvisioningInterceptor.java:89-169 (create + email-link), :181-198 (extractNamesFromPreferences), :256-280 (ATTENDEE default)]
- [Source: infrastructure/lib/lambda/triggers/post-confirmation.ts:99-111 (parseUserPreferences), :222-224 (firstName/lastName/language), :252 (link UPDATE pref_language), :309/323 (INSERT pref_language), :169-176 (getDefaultRole → ATTENDEE)]
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java:141-166 (preferences embeddable), :209-217 (@PrePersist default)] · [Source: .../domain/UserPreferences.java:30-32 (pref_language default "de")]
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserReconciliationService.java:307-350 (createMissingUser), :362-379 (extractNamesFromPreferences), :357 (kept-in-sync-with-JIT comment)]
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/WebMvcConfig.java:22-26 (interceptor on /api/**)]
- [Source: services/company-user-management-service/src/test/java/ch/batbern/companyuser/interceptor/JITUserProvisioningInterceptorTest.java:86-115 (createJwt incl. preferences), :245-273 (email-link, no event), :325-394 (preferences-name + malformed cases)]
- [Source: services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/UserProvisioningAndPatchIntegrationTest.java:1-55 (AbstractIntegrationTest pattern)]
- [Source: docs/architecture/06b-user-lifecycle-sync.md:687-699 (JIT "Safety Net" block to rewrite), :619 (custom:preferences inventory), :693/:831 (anonymous-by-email link semantics), :743-759 (PreAuthentication/is_active context — Part A territory)]
- [Source: .github/doc-drift-mappings.yml:30-32 (CUMS → 06b mapping)]
- Sibling: Story 12.2 (PR 1 — Part A, API-Gateway `is_active` gate) — NOT in scope here.
- ADR-001 (Cognito-for-auth-only), ADR-003/004 (meaningful IDs, enrich via user-api), ADR-010 (federated identity via Cognito).

### Open Questions
- **OQ-1 (AC7 — reconciliation parity).** Plan §5 Part B names only the JIT interceptor; the `UserReconciliationService.createMissingUser` `language` gap is an adjacent divergence found while tracing (the two paths are explicitly kept in sync per `UserReconciliationService.java:357`). Recommendation: fix both in the same commit for consistency. Decide before dev whether to include Task 4 or defer it as a documented residual. (Owner: Nissim.)
- **OQ-2 (plan-vs-reality).** The plan's Part-B background asserts JIT "never reads `custom:preferences`" and "only sets `isActive(true)`"; the current source contradicts this (names + email-link + ATTENDEE already present). This story narrows Part B to the genuine residual (`language`) plus integration-test hardening, and flags the discrepancy so the SSO plan can be corrected. Confirm this narrowing is acceptable rather than re-implementing already-present behaviour. (Owner: Nissim / Winston.)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story, 2026-06-02.

### Debug Log References

- JIT unit tests: `/tmp/12-3-jit-unit.log` (BUILD SUCCESSFUL — 3 new language cases green).
- Targeted unit + integration: `/tmp/12-3-targeted.log` (BUILD SUCCESSFUL). `JITProvisioningIntegrationTest` result XML `tests=3 failures=0 errors=0` (the console doesn't print integration PASSED lines; verified via `build/test-results/.../TEST-...JITProvisioningIntegrationTest.xml`).
- Full CUMS suite: `/tmp/12-3-full-cums.log` (BUILD SUCCESSFUL, 0 failures — no regressions).
- Checkstyle: `/tmp/12-3-checkstyle.log` (checkstyleMain + checkstyleTest BUILD SUCCESSFUL).

### Completion Notes List

- **Pre-impl reality check held (OQ-2 accept-narrowing, confirmed by Nissim):** traced current source — names-from-`custom:preferences` (`:102-113`,`:181-198`), email-link (`:118-128`), ATTENDEE default (`:274-277`) are ALL present and unregressed post-12.1. The genuine residual was `language`, exactly as the story narrowed. No already-present behaviour re-implemented.
- **AC1/AC2/AC5 (JIT language):** new swallow-safe `extractLanguageFromPreferences(Jwt)`; `User.builder()` sets `preferences(language=…)` only when a non-empty language parses, else leaves it null so `@PrePersist` supplies `"de"`. Mirrors `post-confirmation.ts` (`language || 'de'`). Name-extraction contract untouched; non-blocking contract preserved (malformed → null, no throw).
- **AC3/AC4/AC6 (integration, Testcontainers):** `JITProvisioningIntegrationTest` drives real authenticated `/api/**` requests through the registered interceptor via the `jwt()` post-processor. Fresh identity → 1 row (names + `pref_language='fr'` + ATTENDEE) + 1 `JIT_PROVISIONING` event; no-prefs identity → `pref_language='de'`; anonymous-by-email → linked to fresh `sub`, not duplicated, 0 events. Provider-agnostic: no federated branch, no `triggerSource` check, no new endpoint.
- **AC7 (OQ-1 = include, confirmed by Nissim):** identical `language` fix applied to `UserReconciliationService.createMissingUser` + unit test. JIT and the nightly reconciliation path stay in sync (the explicit in-sync comment). No residual divergence left.
- **AC8 (docs same commit):** `06b` JIT block rewritten to "canonical provider-agnostic reconcile path"; inventory row reconciled; ADR-010 referenced; Story 12.8 noted verify-only.
- **Deploy note:** gateway + CUMS service deploy (fast-path/hotswap) — code-only, **no migration** (`pref_language` column already exists; this only populates it on the JIT/reconciliation create path). Risk low (invisible to active users — existing users always match `findByCognitoUserId`/`findByEmail` and are never re-created). **Rollback = revert the commit** (no flag; the `is_active` kill-switch belongs to sibling Story 12.2 / Part A).
- **Out of scope (confirmed):** the API-Gateway `is_active` gate = sibling Story 12.2 (Part A, already merged via #732 + review hardening on this branch); federated IdP wiring = Phase 1 (12.5+); Phase-3 verify = 12.7.

### File List

**company-user-management-service (main)**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/interceptor/JITUserProvisioningInterceptor.java` (M — `extractLanguageFromPreferences` + conditional `preferences` on create)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserReconciliationService.java` (M — same `language`-capture fix, AC7 parity)

**company-user-management-service (test)**
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/interceptor/JITUserProvisioningInterceptorTest.java` (M — 3 language unit tests)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/UserReconciliationServiceTest.java` (M — 1 language unit test)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/JITProvisioningIntegrationTest.java` (NEW — 3 Testcontainers tests: create + default-de + email-link)

**Docs**
- `docs/architecture/06b-user-lifecycle-sync.md` (M — JIT block → canonical provider-agnostic path; inventory row reconciled)

**Sprint tracking**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (M — 12-3 ready-for-dev → in-progress → review)

### Change Log

| Date | Change |
|---|---|
| 2026-06-02 | Story 12.3 implemented (canonical JIT provisioning, SSO PR 1 Part B). JIT + reconciliation now capture `language` from `custom:preferences` on create (mirroring `post-confirmation.ts`), closing the last `custom:preferences` divergence; create + email-link + default-ATTENDEE behaviour locked behind a new Testcontainers `JITProvisioningIntegrationTest` (fresh-create + default-de + anonymous-by-email link). 4 new unit tests + 3 integration tests. OQ-1 (recon parity) included, OQ-2 (narrowing) accepted. `06b` JIT section rewritten as canonical provider-agnostic path (makes 12.7 verify-only). Full CUMS suite green, checkstyle clean. Status → review. |
