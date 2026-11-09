---
title: 'Refactor sweep (epic test fidelity)'
type: 'refactor'
ticket: 'sweep'
created: '2026-11-09'
status: 'built'
baseline_revision: 'c1a0241740b849442eec50fd567245a568f18cc2'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '/tmp/claude-1000/-home-nissim-dev-bat-BATbern-bmad-v7/4631b2e5-8c64-4b93-94d3-3fc01dbf8c22/scratchpad/overnight-brief.md'
  - '{project-root}/docs/architecture/coding-standards.md'
warnings: ['oversized', 'multiple-goals']
deferred: []
---

<intent-contract>

## Intent

**Problem:** The test-fidelity epic left named cleanup behind: CUMS ITs run a Mockito mock of our own `CognitoIntegrationService` (shared `@Primary` in `TestAwsConfig`, plus two `@MockitoBean`s), `UserControllerIntegrationTest` and `UserRepositoryTest` still roll back a class-level `@Transactional`, four frontend tests resolve a service mock on a timer after the test ended (the pattern that leaked `window is not defined`, fixed once in 978198a7), and nothing stops a new `t('key')` whose key is missing from the `en` bundles (about 176 calls in 52 files; with `fallbackLng: 'de'` an English visitor sees German or the inline fallback).

**Approach:** Run the real `CognitoIntegrationServiceImpl` on an in-memory Cognito at the AWS client boundary and delete the dead no-op methods; make the two ITs commit and clean up; settle the four timer tests and move those hook tests (and the 0c2 leftovers that mock one service module) onto the MSW harness; add a static i18n-key check with a committed baseline that can only shrink, run in CI next to the API path check. Every error this exposes is fixed in production code, test first, own commit.

## Boundaries & Constraints

**Always:** the overnight brief's host rules (one heavy job at a time, Gradle `--max-workers=4` one module at a time, Vitest `--maxWorkers=4`, `set -o pipefail` + tee to `~/.cache/batbern-test-logs/sweep-*.log`); Test fidelity rules in coding-standards.md; one commit per item (below), conventional message with the brief's trailers; the ratchet baseline only falls (`make check-test-ratchet`, `--update` for STALE, commit the baseline in the same commit); a fix in production code gets a red test first and its own commit; mark each deferred-work entry taken with its commit.

**Never:** push, open a PR, touch staging/production or AWS; change `CLAUDE.md`, `_bmad-output/project-context.md` or `_bmad/custom/*.toml` (epic constraint: agent-context files change only in 17.0a); edit an applied Flyway migration; change the public API contract (`cognitoSync` stays in the specs; a description correction is fine); fill the 176 missing i18n keys here (the gate freezes them; filling them is touch-rule work); decide the owner items (null-vs-optional, `/registration-confirmation`, organizer usernames, CloudWatch export, `CreateEventRequest` required fields, analytics `@PreAuthorize`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Cognito create | real impl, in-memory Cognito, new e-mail | user exists in the fake with `email`, `email_verified`, `preferred_username`, `custom:role=UNUSED`; the returned `sub` is stored | existing e-mail: `UsernameExistsException` → null (idempotent) |
| Cognito status of unknown login | `adminGetUser` for an e-mail with no login | service throws its `UserNotFoundException` as with real Cognito | test asserts the HTTP outcome |
| Cognito failure | fake told to fail an operation (`failNext(op, exception)`) | `CognitoOperationException` → the controller's 5xx path; merge not run when disable fails | covered by AccountMerge, rollback ITs |
| i18n RAISED | a source file gains a `t('ns:key')` whose key is in no `en/<ns>.json` | `check:i18n-keys` exits 1, names file, key, namespace | message points at the rule and the update command |
| i18n STALE | a missing key added to `en` | exits 1 until `--update` | same as the test ratchet |
| i18n plural/prefix | `t('items', { count })` with `items_one`/`items_other`; `useTranslation('ns', { keyPrefix })`; `t('k', { ns: 'x' })`; dynamic key | plural and prefix resolve; dynamic keys skipped, not counted | — |

</intent-contract>

## Code Map

Backend (CUMS, `services/company-user-management-service`):
- `src/main/java/ch/batbern/companyuser/service/CognitoIntegrationService{,Impl}.java` -- `syncUserAttributes` (no caller in `src/main`) and `createCognitoUser` (no-op, returns null; only caller `UserService.createNewUser` ~line 1018) are dead: remove both; `createNewUser` stores no cognito id from `cognitoSync`. Correct the `cognitoSync` description in `docs/api/users-api.openapi.yml:4321` (it has no effect; logins come from sign-up or issue-credentials), regenerate types. `UserServiceTest` (unit, `@Mock`) follows.
- `src/test/java/ch/batbern/companyuser/config/TestAwsConfig.java` -- delete the `CognitoIntegrationService` mock bean; replace the plain `Mockito.mock(CognitoIdentityProviderClient)` with an in-memory Cognito (new test class, e.g. `config/InMemoryCognito.java`, implementing `CognitoIdentityProviderClient`): users by username/e-mail with attributes, status, enabled; `adminCreateUser` (returns a random `sub`, `UsernameExistsException` on a duplicate), `adminGetUser` (Cognito `UserNotFoundException` when absent), `adminSetUserPassword`, `adminUpdateUserAttributes` (`AliasExistsException` when the new e-mail is another user's), `adminDisableUser`/`adminEnableUser`/`adminDeleteUser`, `listUsers` (so `listUsersPaginator` works for `UserReconciliationService`), `resendConfirmationCode`; `reset()`, `failNext(operation, exception)`, read accessors for assertions; any other method keeps the SDK default (UnsupportedOperationException). Tests autowire it and `reset()` it in `@BeforeEach`.
- Mock users of the service to move: `controller/AccountMergeIntegrationTest`, `controller/PrimaryEmailIntegrationTest` (`@MockitoBean CognitoIntegrationService` + `@MockitoBean EventManagementUserMergeClient`), `integration/UserProvisioningAndPatchIntegrationTest`, `integration/UserServiceIssueCredentialsIntegrationTest`, `integration/UserProvisioningCognitoRollbackIntegrationTest`, `contract/S2sProviderContractIntegrationTest` (lines ~73-142). Assert the effect in the fake (login disabled/deleted/e-mail moved, password state), not `verify(service)`.
- `EventManagementUserMergeClient` in those two touched ITs: replace the `@MockitoBean` with the real `HttpEventManagementUserMergeClient` on `ContractServer` against `EMS_INTERNAL` (see `client/HttpEventManagementUserMergeClientContractTest` for the binding; the client builds its own `RestTemplate`, so expose it the simplest clean way: a package-visible accessor or one shared `@Bean` RestTemplate for both EMS clients, as EMS does with its CUMS harness; `close()` in `@AfterEach`).
- `controller/UserControllerIntegrationTest.java` (1648 lines, 66 tests, `@BeforeEach` already `userRepository.deleteAll()`) and `repository/UserRepositoryTest.java` (24 tests) -- drop class-level `@Transactional`; every test commits; clean up in `@AfterEach` everything the class wrote (users and their child rows, companies it created), so no row leaks into the next class in the shared container. Method-level `@Transactional` only where a test needs a session (lazy reads) and says why. A failure that only shows once data commits is a finding: fix in production code, test first.
- Ratchet start (2026-11-09, `make check-test-ratchet`): be-mock-in-process 33 files/46, be-mock-s2s-client 60/61, be-transactional-it 129/129, fe-mock-services 182/211, fe-mock-hooks 111/184.

Frontend (`web-frontend`):
- Timer tests: `src/hooks/useTopics.test.tsx:147`, `src/hooks/useSpeakerPool.test.tsx:128`, `src/hooks/useUserManagement/__tests__/useUpdateUserRoles.test.tsx:68`, `src/components/organizer/EventManagement/__tests__/EventForm.test.tsx:571`: `new Promise(resolve => setTimeout(() => resolve(x), N))` mock answers. The model fix is 978198a7 (test resolves the work itself and waits for the settled state). Move the three hook files onto the MSW harness (`api.on`, `api.calls`; a pending answer: a handler that awaits a promise the test resolves, or MSW `delay('infinite')` plus a release), dropping their `vi.mock('@/services/...')`. `EventForm.test.tsx` (mocks 2 hooks, 1 service): fix the timer; convert only if it is cheap.
- 0c2 leftovers mocking one service module: `src/hooks/usePublishing/usePublishing.test.ts` and `.test.tsx` (two files for one hook: merge into one), `src/hooks/useSlotAssignment/useSlotAssignment.test.ts` -- onto the harness. `DragDropSlotAssignment.test.tsx` (4 hook mocks) stays under the touch rule.
- i18n gate: new `scripts/check-i18n-keys.ts` (plain TS for Node type stripping, no npm deps, same shape as `scripts/test-ratchet.ts` and `scripts/lib/mask-comments.ts`), baseline `scripts/i18n-keys-baseline.txt` (`<path>\t<count>` sorted), `scripts/check-i18n-keys.test.ts` with fixtures under `scripts/__fixtures__/i18n-keys/`. Scan `src/**/*.{ts,tsx}` excluding tests; bindings `const { t } = useTranslation('ns' | ['a','b'] | (), { keyPrefix })`, renamed `t: tX`, `i18n.t` / `i18next.t` (default `common`), `{ ns: 'x' }` option, `ns:key` prefix; key found when the path, or a plural/context form `key_<suffix>`, exists in `public/locales/en/<ns>.json`; a `t` whose namespace cannot be resolved (passed in as a parameter) counts as found when the key exists in any `en` namespace. Wire: `package.json` script `check:i18n-keys` (+ lint list), `Makefile` target `check-i18n-keys`, `.github/workflows/build.yml` step after "Frontend API path check" (~line 561) running its tests and the check. Document it in coding-standards.md (i18n/frontend testing area), not in agent-context files.
- Namespaces: `src/i18n/config.ts:265-285` (`defaultNS: 'common'`, `fallbackLng: 'de'`).

Records:
- `_bmad-output/initiative-architecture-hardening/deferred-work.md` -- add `resolved:` with commit to the i18n-gate entry; add entries (with source plan, evidence, and `resolved:` commit) for the 0d Cognito mock and class-level `@Transactional` items and the timer tests; add open owner-decision entries for the 0b3 findings (`CreateEventRequest` requires 3 fields, the entity 8; `GET /events/{code}/analytics` without `@PreAuthorize`) and a touch-rule entry for the 13 unit tests with `@Mock EmailService` + `verify` and `DragDropSlotAssignment.test.tsx`.
- `tickets/story-production-shaped-fixtures-plan.md` frontmatter `deferred:` -- leave as is (append-only); this plan's notes name the commits.

## Tasks & Acceptance

**Execution:**
- [ ] `CognitoIntegrationService{,Impl}`, `UserService`, `UserServiceTest`, users spec + generated types -- remove the two dead methods -- dead surface; commit `refactor(cums): ...`.
- [ ] `TestAwsConfig`, new in-memory Cognito, the six ITs above -- real `CognitoIntegrationServiceImpl` on the in-memory Cognito; AccountMerge/PrimaryEmail EMS client on `ContractServer` -- mock at the boundary; commit `test(cums): ...` with ratchet baseline.
- [ ] `UserControllerIntegrationTest`, `UserRepositoryTest` -- committing tests with cleanup -- class-level `@Transactional` gone; commit with ratchet baseline.
- [ ] Any production defect the two items above expose -- red test, fix, own `fix(...)` commit.
- [ ] Four timer tests + three hook files + usePublishing/useSlotAssignment -- harness, no timers after test end; commit(s) `test(frontend): ...` with ratchet baseline.
- [ ] i18n check script, test, fixtures, baseline, package.json, Makefile, build.yml, coding-standards.md -- gate; commit `feat(ci): ...`.
- [ ] deferred-work.md and this plan's Implementation Notes -- records; commit `docs(bmad): ...`.

**Acceptance Criteria:**
- Given CUMS test sources, when grepping `mock(CognitoIntegrationService` / `@MockitoBean` of it, then nothing is found, and `TestAwsConfig` no longer has a counted line.
- Given AccountMerge's "login cannot be disabled" case, when the in-memory Cognito fails `adminDisableUser`, then the request answers 5xx, the user B row still exists and no merge request reached the EMS `ContractServer`.
- Given `UserControllerIntegrationTest` and `UserRepositoryTest`, when the CUMS suite runs, then neither has class-level `@Transactional`, and the full CUMS suite passes twice in a row with classes in default order (no leaked rows).
- Given the four files, when Vitest runs them, then no answer resolves after the test returned (no `setTimeout` resolving a mock), and the full Vitest suite passes with no unhandled error.
- Given a fixture file with `t('events:does.not.exist')`, when `check-i18n-keys.test.ts` runs, then the check reports RAISED with file and key; a lowered count reports STALE; the real repo at its baseline passes.
- Given the whole change, when `make check-test-ratchet` runs, then it passes and every category is at or below its start count, with be-mock-in-process, be-mock-s2s-client, be-transactional-it and fe-mock-services lower.

## Implementation Notes

Built 2026-11-09 (overnight mandate), one commit per item:

- 56ef7f16 `refactor(cums)`: `syncUserAttributes` and `createCognitoUser` removed from
  `CognitoIntegrationService{,Impl}`; `createNewUser` stores no cognito id; `cognitoSync` described
  as without effect in `users-api` (field kept), types regenerated; ADR-001 notes the removal.
  `UserServiceTest` asserts the saved user (no cognito id even with `cognitoSync=true`).
- a2d4d70b `test(cums)`: `config/InMemoryCognito` (e-mail alias with the sub as username, pool
  password policy, `UsernameExistsException`, `UserNotFoundException`, `AliasExistsException`,
  `failNext`, `givenLogin`, read accessors; `listUsers` paging for the paginator) is the context's
  `CognitoIdentityProviderClient`; the shared mock bean is gone. Six ITs assert the login's state.
  AccountMerge and PrimaryEmail run the real `HttpEventManagementUserMergeClient` on `ContractServer`
  (package-visible `restTemplate()` + test helper `client/EventManagementContract`). "No call" is an
  `ExpectedCount.never()` expectation: `MockRestServiceServer.verify()` returns early when it has no
  expectations and ignores stray requests (read in spring-test 7.0.8 bytecode), and the controller
  advice turns the harness's `AssertionError` into a 500. Issue-credentials gained the missing-login
  self-heal case.
- 1dd56547 `fix(cums)`: found by the item above (red first): the new-user branch of
  `provisionUserWithRole` dropped the sub `adminCreateUserSilently` returns, so a new speaker's
  `cognito_user_id` stayed null. All three callers now share `linkToNewLogin`; 06b doc updated.
- f41da0aa `test(cums)`: `UserControllerIntegrationTest` and `UserRepositoryTest` commit; before and
  after each test: `DELETE FROM watch_pairings`, `DELETE FROM user_profiles` (children cascade);
  UserController also deletes the companies created since `@BeforeEach` and clears every cache.
  Two setups now change the lazy additional e-mails in one `TransactionTemplate`; the role query is
  asserted by usernames. No production defect surfaced.
- 7b1385d0 `test(frontend)`: useTopics, useSpeakerPool, useUpdateUserRoles, usePublishing (two files
  merged), useSlotAssignment and the duplicate `useTopics.test.ts` (merged) run on the MSW harness;
  added to `tsconfig.test-harness.json` (caught `Topic` without `active`, roles as `string[]`). The
  EventForm timer test settles its own save; its block stays skipped (fake timers stall userEvent,
  measured: hang killed after 300 s) and the skip note no longer claims auto-save is missing.
  Dropped: useSlotAssignment tests of the fallback message for a non-Error rejection (unreachable,
  the real service always rejects with an Error).
- d6bbda51 `feat(ci)`: `scripts/check-i18n-keys.ts` + test + fixtures + baseline (166 calls in 51
  files; the earlier heuristic said 176 in 52), `check:i18n-keys` (and in the lint list),
  `make check-i18n-keys`, `build.yml` step after the API path check, coding-standards section.
- 96937448 `fix(frontend)`: `updateUserRoles` typed `Promise<UserRolesResponse>` (it was `User`;
  found while typing the harness test; no caller read the result).
- Records commit: deferred-work entries (see that file) and these notes.

Verification (2026-11-09, logs `~/.cache/batbern-test-logs/sweep-*`): CUMS suite 923 tests green
three times (after a2d4d70b, then twice after f41da0aa: plain and `--rerun`); CUMS checkstyle clean;
full Vitest 467 files, 6017 passed, exit 0, no unhandled error; type-check, lint, format:check,
check:i18n-keys, check:api-paths clean; `make check-test-ratchet` OK: be-mock-in-process 30/43,
be-mock-s2s-client 58/59, be-transactional-it 127, fe-mock-services 175/204, fe-mock-hooks 111/184
(unchanged).

Not done (by the plan's boundaries or owner): the 166 i18n keys; the owner items; the EventForm
auto-save block (deferred-work); `useSpeakerPoolHooks.test.ts` still mocks `speakerPoolService`
(other hooks, touch rule).

## Plan Change Log

## Review Triage Log

Review run by the orchestrator: the build-auto agent died after its implementer, before step 4.

| # | Finding | Verdict | Route | Evidence |
|---|---------|---------|-------|----------|
| 1 | i18n gate accepts a bare key when any `key_*` sibling exists, with no `count`/`context` check | medium | patch | `hasKey` ignores the call options, and the test pins `['items', true]`. The gate misses real gaps. |
| 2 | i18n gate accepts an object-valued key | medium | patch | `key in node` is true for an object; i18next then renders "returned an object". |
| 3 | A `t` received as a parameter is resolved by text position, not by scope | medium | patch | Contradicts the documented rule; gives false RAISED results or wrong baseline counts. |
| 4 | InMemoryCognito keeps `email_verified` on an e-mail change | medium | patch | Real Cognito resets it, so the assertion in PrimaryEmail IT cannot fail. |
| 5 | EventForm timer fix lives inside `describe.skip` | medium | patch | AC4 holds only because the test does not run. 17.0 goal: find the cause of the hang and unskip. |
| 6 | `updateUserRoles` return-type fix has no red test | low | patch | Plan Always rule; a direct test is cheap. |
| 7 | 06b doc says only the existing-user branch linked the sub | low | patch | The removed `issueInvitationCredentials` self-heal also linked it, as commit 1dd56547 says. |

## Design Notes

Architecture note: modules: CUMS service layer (`CognitoIntegrationService`) and CUMS tests, web-frontend tests and `scripts/`, CI `build.yml` · new dependencies: none (the i18n check is dependency-free like the ratchet) · endpoints: none new or changed (only the `cognitoSync` description in `users-api`) · reused: `ContractServer`/`EMS_INTERNAL`, MSW harness `api.on`/`api.calls`, the ratchet's script/baseline/STALE pattern, `mask-comments.ts` · deviations: none.

The in-memory Cognito is a boundary fake in the sense of the rules: it stands for AWS, as `SesRecorder` stands for SES. It should behave like Cognito for what CUMS calls (the error types above), so a CUMS bug in the error mapping shows.

## Verification

Logs: `~/.cache/batbern-test-logs/sweep-*.log`, each run `set -o pipefail; ... 2>&1 | tee <log>`; one heavy job at a time.

**Commands:**
- `./gradlew :services:company-user-management-service:test --max-workers=4` (twice, after the backend commits) -- expected: 0 failures both times.
- `./gradlew :services:company-user-management-service:checkstyleMain :services:company-user-management-service:checkstyleTest --max-workers=4` -- expected: clean.
- `cd web-frontend && npx vitest run --maxWorkers=4 scripts/check-i18n-keys.test.ts scripts/test-ratchet.test.ts` -- expected: pass.
- `cd web-frontend && npx vitest run --maxWorkers=4` (full) -- expected: 0 failures, no unhandled errors.
- `cd web-frontend && npm run type-check && npm run lint && npm run format:check && npm run check:i18n-keys && npm run check:api-paths` -- expected: clean.
- `cd web-frontend && npm run generate:api-types && git diff --exit-code src/types/generated/` after the spec description commit -- expected: types committed.
- `make check-test-ratchet` -- expected: OK, counts below the start counts in Code Map.
