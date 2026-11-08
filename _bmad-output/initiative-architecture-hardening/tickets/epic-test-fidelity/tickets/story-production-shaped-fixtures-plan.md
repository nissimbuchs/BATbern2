---
title: 'Production-shaped fixtures (17.0d)'
type: 'feature'
ticket: '0d'
created: '2026-11-08'
status: 'built'
baseline_revision: '77071b06057989de23e43dc908196f77537171bd'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/plans/test-fidelity-proposal-2026-11.md'
  - '/tmp/claude-1000/-home-nissim-dev-bat-BATbern-bmad-v7/4631b2e5-8c64-4b93-94d3-3fc01dbf8c22/scratchpad/overnight-brief.md'
warnings: ['oversized']
deferred:
  - summary: >-
      CUMS TestAwsConfig still holds a Mockito mock of our own CognitoIntegrationService.
    evidence: |-
      Touched in 17.0d (createCognitoUser now returns null as the real impl does). The real
      CognitoIntegrationServiceImpl could run on the already-mocked CognitoIdentityProviderClient
      boundary; removing the shared mock affects every CUMS IT, so it belongs to the refactor sweep.
    location: >-
      services/company-user-management-service/src/test/java/ch/batbern/companyuser/config/TestAwsConfig.java
    severity: medium
  - summary: >-
      UserControllerIntegrationTest and UserRepositoryTest keep class-level @Transactional.
    evidence: |-
      Pre-existing (counted by the be-transactional-it ratchet). FlushBeforeRollback flushes before
      rollback, so constraints run; converting 153 tests to committing ITs with cleanup is sweep work.
    location: >-
      services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/UserControllerIntegrationTest.java
    severity: low
---

<intent-contract>

## Intent

**Problem:** Test data is ASCII, lower-case, unique and small ("John Doe", `query: 'Anna'`, five users), so unicode names, case, namesakes, page limits and sort ties stay untested until production hits them (#1034 umlaut search and 32 "Matthias", #1104 "Hông Ân" → 500, #1107 mixed-case e-mails, #1131 sort ties). Each regression test invents its own table.

**Approach:** One shared fixture set per side, used by default. Frontend: typed builders in `web-frontend/src/test/fixtures/`, and the MSW harness answers the people, event and registration list operations from them (with real paging). Backend: a people set in shared-kernel test fixtures, exposed by `AbstractIntegrationTest`, plus an EMS event/registration set. A guard test per side asserts every listed shape is present; the #1034 and #1104 regression tests run on the shared sets.

**Decisions (taken on the owner's behalf under the overnight mandate, 2026-11-08):** (1) "Default in the backend test base" means the base class exposes the set; it does not seed every IT's database (seeding would change row counts in hundreds of existing ITs). (2) "Default in the harness" means `listUsers`, `searchUsers`, `listEvents`, `listRegistrations` answer from the fixture set when no override is set; every other operation keeps its spec example. (3) Event dates are relative to the current clock, never fixed calendar dates (#1069, #1241 time bombs). (4) `project-context.md` is not edited (epic constraint: agent-context files change only in 17.0a); `coding-standards.md` carries the rule, and project-context already points there.

## Boundaries & Constraints

**Always:** Fixture shapes: `Hông Ân` (NFC), `O’Neill` (typographic apostrophe), `王`, a mixed-case e-mail, an IDN e-mail domain (ASCII local part: SES has no SMTPUTF8), two people with the identical full name, more than 20 people sharing the first name "Matthias" (more rows than the default page limit 20), ties in the sort key, past and future events, every registration status. Usernames match `SlugGenerationService` output and `chk_username_format`. Frontend fixtures are typed from `src/types/generated/` and type-checked (`tsconfig.test-harness.json`). Backend registration statuses cover exactly what `registrations_status_check` allows (read from the database, not hard-coded). Personal data: invented names only, e-mails `@example.com`/`@example.ch` or an IDN under a reserved/example-style domain, plus `success+…@simulator.amazonses.com` where mail must arrive. Tests touched move off own-code mocks (touch rule); the ratchet does not rise. Any production bug the fixtures expose is fixed test-first in this story.

**Never:** No seeding of fixture rows into every IT. No new `vi.mock` of own code, no per-file `setupServer`. No conversion beyond the #1034/#1104 regression files (0c2 owns the 10 worst files). No edit to `username-generation-cases.json` semantics, applied Flyway migrations, CLAUDE.md, project-context or `_bmad/custom`. No staging/production access.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Default people list | harness, `GET /api/v1/users?page=2&limit=20`, no override | page 2 of the fixture people, `pagination.totalItems` > 20, `hasNext`/`hasPrev` right | none |
| Default search | `GET /users/search?query=Stürmer&limit=20` | fixture people whose folded name/username/e-mail contains the folded query (case, accents, ü=ue), capped at `limit` | none |
| Namesakes | `searchUsers('Matthias', 20)` | 20 returned, more exist | none |
| Override wins | `api.on('searchUsers', …)` | override body, fixtures ignored | none |
| Backend constraint | every fixture person through CUMS `POST /internal/users/get-or-create` | 200, username = fixture username, row persisted (constraint ran) | test fails with DB error |
| Status coverage | EMS fixture registrations persisted | statuses == values of `registrations_status_check` | guard fails naming the missing status |

</intent-contract>

## Code Map

- `web-frontend/src/test/msw/server.ts` -- `answer()`: after `harnessState.override(op.key)` misses, `defaultResponse(op)`. Add a fixture-default lookup by `op.key` before the spec example. `api.ts` -- typed `ResponseBody<K,S>`, `Operations`; reuse for fixture default typing. `setup.ts` starts the server globally.
- `web-frontend/tsconfig.test-harness.json` -- explicit include list; add `src/test/fixtures/**/*.ts` and every converted test file.
- Generated types: `user-api.types.ts` (`UserResponse`, `UserSearchResponse` :1849, `PaginatedUserResponse` :2071), `event-registrations-api.types.ts` (`Registration` :584, status union `REGISTERED|WAITLIST|CONFIRMED|CANCELLED|ATTENDED`; `listRegistrations` returns `{data, pagination}`), `events-core-api.types.ts` (`listEvents` :2079).
- Spec semantics for search: `docs/api/users-api.openapi.yml` `listUsers.search` (every word, any order, ignoring case, accents, ü=ue=u), `searchUsers` (`query` required, `limit` max 100 default 20).
- #1034 frontend regressions: `src/services/api/userManagementApi.test.ts:321-340` (axios-mock-adapter compares params objects, not the wire URL: move these cases onto the harness and assert `api.calls('searchUsers')[0].url` encoding); `src/components/shared/__tests__/UserAutocomplete.test.tsx` (`vi.mock` userManagementApi + `react-i18next`: convert fully to harness + fixtures); `src/components/organizer/SpeakerDrawer/__tests__/PromoteSpeakerSubView.test.tsx:87-181` namesake prefill (mocks userManagementApi, useSpeakerPool, UserAutocomplete, UserCreateEditModal: at least drop the userManagementApi mock onto the harness with fixtures; drop the others where the real module renders under the harness).
- #1104 frontend: `src/utils/validation/personName.test.ts`, `src/utils/validation/email.test.ts`, `src/components/auth/RegistrationStep1/RegistrationStep1.test.tsx` -- drive their unicode/IDN tables from the fixture set (keep extra edge cases they have).
- Backend: `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/AbstractIntegrationTest.java` (expose the set); new package `ch.batbern.shared.test.fixtures`. `SlugGenerationService.generateUsername(first,last,email)`, `USERNAME_PATTERN` `^[a-z]+\.[a-z]+(\.[0-9]+)?$`. Golden `shared-kernel/src/test/resources/username-generation-cases.json` (Hông Ân Meier → `hongan.meier`, Jean-Luc O’Neill → `jeanluc.oneill`).
- #1104 backend: `services/company-user-management-service/src/test/java/.../controller/UserControllerIntegrationTest.java:842-866` `@CsvSource` table → `@MethodSource` over the shared people (keep the Cyrillic/CJK email-derived rows). The class is `@Transactional`; the `findByUsername` read forces the flush.
- #1034 backend: `.../repository/UserRepositoryTest.java:344-395` builds 30 "Matthias Filler{i}" by hand → use the shared namesakes; also add an umlaut case (`searchRanked("Stürmer")` finds the `Stuermer`/`Stürmer` fixture).
- EMS: `Event.builder()` / `Registration.builder()` pattern in `services/event-management-service/src/test/java/ch/batbern/events/forwarding/CalendarDeclineServiceIntegrationTest.java:166-195`; `registrations_status_check` (V74: registered, waitlist, confirmed, cancelled, attended); `Registration.status` is a lowercase `String`.
- Ratchet: `web-frontend/scripts/test-ratchet.ts`, baseline `scripts/test-ratchet-baseline.txt`, `make check-test-ratchet`.

## Tasks & Acceptance

**Execution:**
- [x] `web-frontend/src/test/fixtures/people.ts`, `events.ts`, `registrations.ts`, `index.ts` -- typed builders (`aUser(overrides)`, `people` set, `namesakes`, `pastEvent/futureEvent` relative to `Date.now()`, a registration per status) -- the shared FE set.
- [x] `web-frontend/src/test/fixtures/fixtures.test.ts` -- guard: each listed shape present (by predicate, e.g. a name with a combining-free Vietnamese diacritic, `’`, a CJK char, `email !== email.toLowerCase()`, a non-ASCII domain, a duplicate full name, > 20 "Matthias", a sort tie, a past and a future event, every status of the generated union) -- AC 1.
- [x] `web-frontend/src/test/msw/fixtureDefaults.ts` + `server.ts` -- fixture-backed defaults for `listUsers`, `searchUsers`, `listEvents`, `listRegistrations` with paging from `page`/`limit` and the spec's folded search; harness test rows for the matrix.
- [x] Full Vitest run -- fix every test the new defaults break (explicit `api.on` where a test needs a specific body; a real bug gets a test first).
- [x] The #1034/#1104 frontend files above -- onto the harness and fixtures; ratchet `--update` for the counts that fell.
- [x] `shared-kernel/src/testFixtures/java/ch/batbern/shared/test/fixtures/ProductionShapedPeople.java` (+ `Person` record) -- the backend people set; `AbstractIntegrationTest` exposes it (`protected static final` or accessor) with Javadoc.
- [x] `shared-kernel/src/test/java/.../fixtures/ProductionShapedPeopleTest.java` -- guard: each shape present; every username equals `SlugGenerationService` output and matches `USERNAME_PATTERN`.
- [x] CUMS `UserControllerIntegrationTest`, `UserRepositoryTest` -- #1104 and #1034 regressions on the shared set.
- [x] EMS `src/test/java/ch/batbern/events/fixtures/ProductionShapedEvents.java` + `ProductionShapedEventsIntegrationTest` -- past/future events (clock-relative), a registration per status with fixture people; the IT persists them and asserts covered statuses equal the values in `registrations_status_check` (query `pg_get_constraintdef`).
- [x] `docs/architecture/coding-standards.md` -- "Production-shaped fixtures (17.0d)" under Test fidelity rules: where the sets live, harness defaults, clock-relative dates, ASCII-only data is the exception.

**Acceptance Criteria:**
- Given the frontend guard test, when a listed shape is removed from the fixture set, then it fails naming the shape (proved once by a local edit, recorded in Implementation Notes).
- Given the backend guard tests (shared-kernel unit, EMS IT), when run, then every listed shape and every DB-allowed registration status is present.
- Given the #1034 tests (FE search URL with "Stürmer", namesake prefill, BE ranking with > 20 namesakes) and the #1104 tests (CUMS get-or-create for every fixture person incl. `Hông Ân`), when run, then they pass and read their data from the shared sets.
- Given the full frontend suite with fixture defaults active, when run, then 0 failures, `npm run type-check` clean, and `make check-test-ratchet` shows no RAISED line.

## Implementation Notes

- AC1 proof (2026-11-08, local edit, reverted): replacing `'O’Neill'` by `"O'Neill"` in
  `people.ts` made exactly one guard test fail: `contains a typographic apostrophe (O’Neill)`,
  message `the fixture set lost: a typographic apostrophe (O’Neill)`. The guard also has a permanent
  positive control: an ASCII-only, unique, one-person set fails every shape predicate.
- Regression bite checked once each: re-introducing `encodeURIComponent(query)` in `searchUsers`
  fails the two umlaut/space tests of `userManagementApi.search.test.ts`; re-introducing the
  `users[0]` pick in `resolveSpeakerUserByName` fails both ambiguous-fallback tests of
  `PromoteSpeakerSubView.test.tsx`.
- Found by the fixtures, fixed: (1) CUMS `TestAwsConfig` stubbed `createCognitoUser` with a constant
  `"cognito-test-user-id"`, production returns `null`; the second namesake through get-or-create hit
  `user_profiles_cognito_user_id_key`. The stub now returns `null` like production (full CUMS suite
  green). (2) `user_profiles.company_id` is `VARCHAR(12)`: a fixture company id of 14 characters
  failed the insert; both guards now check the column limit. Neither is a production bug.
- Full frontend suite: one test broke on the new defaults (`eventApiClient.test.ts` asked for page 2
  of 10 and expected rows; now page 2 of 1). The 4 unhandled "Maximum update depth exceeded" errors
  from `ForgotPasswordForm.test.tsx` are pre-existing (same 4 in the 17.0c1 verification log).
- Ratchet: 6 counts fell (PromoteSpeakerSubView: services, hooks, 2 components; UserAutocomplete:
  services, i18n; RegistrationStep1: services, `authService` replaced by the Amplify boundary).

## Plan Change Log

- Search fold of the FE default approximates SQL `batbern_fold` (lower case; ß, ø, æ, œ, đ, ł, þ
  mapped as `unaccent` does; other accents stripped via NFD; `-'’_` → space; `ae/oe/ue` → `a/o/u`)
  instead of the plan's `ü→ue` then strip: the spec says ü = ue = u, and only this form makes
  "Bruennimann" and "Brünnimann" fold to the same string. Letters `unaccent` maps beyond these are
  not covered.
- CUMS #1104 regression: `@MethodSource` over `PEOPLE`, each case first creating the namesakes that
  precede the person (`ProductionShapedPeople.namesakesBefore`), so the second "Matthias Keller"
  is asserted as `matthias.keller.2`. The Cyrillic/CJK email-derived rows stay as `@CsvSource`.
- `listEvents` default excludes `ARCHIVED` unless `includeArchived=true` (the spec's documented
  default); `listUsers` honours `filter={"role":...}`; other filters and `sort` are ignored
  (stand-in, documented in `fixtureDefaults.ts`).
- Names are invented, including the #1034 umlaut target "Matthias Brünnimann"
  (`matthias.bruennimann`); the plan's "Stürmer" example was replaced after review because
  "Matthias Stürmer" is a real, identifiable person. Real-looking surnames and domains in the
  converted tests (Germann, Imsand, `bfh.ch`, `dvbern.ch`) were dropped. Addresses are
  `example.com` or an IDN under the reserved `.example` TLD only (review: `example.ch` is not
  reserved); both guards enforce it.
- Review follow-ups: the fixture-backed default bodies are validated against the response schemas
  (`fixtureDefaults.test.ts`, same validator as `examples.test.ts`); the EMS fixture registers the
  IDN-e-mail attendee too; the `createdAt` tie is frontend-only.

## Review Triage Log

### 2026-11-08 — Review pass
- verdicts: 9 findings — high 0, medium 3, low 4, false 0, maybe-false 0 (2 medium/low deferred as pre-existing)
- findings:
  - `[medium]` `[patch]` Fixture contained a real, identifiable person ("Matthias Stürmer", company fhbern) — replaced by invented "Matthias Brünnimann" (`matthias.bruennimann`, no company) in both sets and all tests; last `bfh.ch`/`dvbern.ch` addresses removed from UserRepositoryTest.
  - `[medium]` `[patch]` `@example.ch` is not reserved; CLAUDE.md allows `@example.com` only — all addresses now `example.com` or `*.example` (IDN); both guards tightened.
  - `[medium]` `[defer]` TestAwsConfig keeps a mock of own CognitoIntegrationService — pre-existing shared mock, only its return value was corrected; removal touches every CUMS IT, deferred to the sweep.
  - `[low]` `[defer]` Touched CUMS ITs keep class-level @Transactional — pre-existing, FlushBeforeRollback runs the constraints; ratchet-counted, deferred to the sweep.
  - `[medium]` `[patch]` Fixture-backed default bodies not validated against the response schema — `fixtureDefaults.test.ts` now validates one request per operation with the `examples.test.ts` validator, covers exactly FIXTURE_BACKED_OPERATIONS, with a positive control.
  - `[low]` `[patch]` EMS Javadoc claimed an IDN attendee that never registered — IDN_EMAIL now registered (confirmed, future event) and asserted stored as entered.
  - `[low]` `[patch]` coding-standards claimed a backend createdAt tie — text now says the createdAt tie is frontend-only.
  - `[low]` `[patch]` searchFold claimed to equal batbern_fold (unaccent maps ß, ø, æ…) — mappings added, wording changed to "approximates", gap named.
  - `[low]` `[patch]` UserAutocomplete test name said "ambiguous first name" for an identical-full-name case — renamed `..._when_twoPeopleShareTheFullName`.

## Design Notes

Architecture note: modules: web-frontend test infra (`src/test/fixtures`, `src/test/msw`), shared-kernel test fixtures, CUMS and EMS tests, coding-standards · new dependencies: none · endpoints: none (only existing specced operations answered) · reused: 0c1 harness and its typed `api`, generated types, `SlugGenerationService`, golden username cases, `AbstractIntegrationTest` · deviations: none.

Search fold in the FE default: lowercase, `ü→ue, ä→ae, ö→oe` and NFD-strip both sides, every word must match (spec of `listUsers.search`). It is a stand-in, not a re-implementation of ranking: tests that depend on ranking set `api.on`.

## Verification

Rack rules: one heavy job at a time; Vitest `--maxWorkers=4`; Gradle one module, `--max-workers=4`; logs in `~/.cache/batbern-test-logs/0d-*`; `set -o pipefail`.

**Commands:**
- `cd web-frontend && npx vitest run --maxWorkers=4 src/test` -- expected: pass.
- `cd web-frontend && npx vitest run --maxWorkers=4` -- expected: 0 failures.
- `cd web-frontend && npm run type-check && npm run lint && npm run format:check` -- expected: clean.
- `./gradlew :shared-kernel:test :shared-kernel:publishToMavenLocal --max-workers=4` -- expected: pass.
- `./gradlew :services:company-user-management-service:test --tests '*UserControllerIntegrationTest' --tests '*UserRepositoryTest' --max-workers=4` -- expected: pass.
- `./gradlew :services:event-management-service:test --tests '*ProductionShapedEvents*' --max-workers=4` -- expected: pass.
- `make check-test-ratchet` -- expected: no RAISED, no STALE after `--update`.

## Auto Run Result

Status: built (2026-11-08, local commits only, nothing pushed).

**Summary:** One production-shaped fixture set per side, used by default. Frontend `web-frontend/src/test/fixtures/` (typed from generated types) and MSW harness defaults for `listUsers`, `searchUsers`, `listEvents`, `listRegistrations` with real paging and a folded search; backend `ProductionShapedPeople` (shared-kernel test fixtures, exposed as `AbstractIntegrationTest.PEOPLE`, not seeded) and EMS `ProductionShapedEvents`. Guard tests on both sides; #1034 and #1104 regressions run on the shared sets.

**Files:** fixtures + guard (`web-frontend/src/test/fixtures/*`), harness defaults (`src/test/msw/fixtureDefaults.ts`, `server.ts`, `fixtureDefaults.test.ts`), converted tests (UserAutocomplete, PromoteSpeakerSubView, userManagementApi.search, RegistrationStep1, personName, email, eventApiClient), `tsconfig.test-harness.json`, ratchet baseline (6 lines fell); shared-kernel `ProductionShapedPeople` + test, `AbstractIntegrationTest`; CUMS `UserControllerIntegrationTest`, `UserRepositoryTest`, `TestAwsConfig` (Cognito stub returns null like production); EMS `ProductionShapedEvents` + IT; `coding-standards.md`.

**Review:** 7 patches applied, 2 deferred (pre-existing own-code Cognito mock; class-level @Transactional), 0 rejected.

**Follow-up review recommended:** false. Patched 0 high, 3 medium, 4 low on a first pass; the medium ones were data/doc corrections plus one added validation test, all re-verified, no unverified risk remains that can be named.

**Verification:** Vitest targeted (13 files, 285 tests) pass after patches; full Vitest before patches 464 files / 5990 tests pass, exit 1 on 4 pre-existing ForgotPasswordForm "Maximum update depth" errors (same in the 17.0c1 logs). `npm run type-check`, `npm run lint`, prettier clean. `make check-test-ratchet` no RAISED/STALE. Gradle (implementing agent, result XML checked): ProductionShapedPeopleTest 16/16, CUMS UserRepositoryTest 24/24, UserControllerIntegrationTest 129 (8 skipped, 0 failed), EMS ProductionShapedEventsIntegrationTest 4/4; full CUMS suite green before patches. Full EMS suite not run (only change there: new static field in the base class and new test files).

**Residual risks:** Tests elsewhere that relied on spec examples for the four list operations now get fixture data (only one needed a change in this suite). The FE search fold approximates `batbern_fold`. ForgotPasswordForm errors keep the full Vitest exit code non-zero.

