# Test fidelity: why 10,000 tests missed the bugs, and what to change

Winston, 2026-11-07. Proposal, awaiting owner decision. Evidence table: appendix (57 bug instances
from 41 bug-fix PRs merged to `develop` since 2026-08-01).

## Conclusion

1. **The owner's suspicion is right, and it is the largest single cause.** In 15 of 57 production
   bugs, a mocked collaborator hid the defect. The mock sat exactly on the code that was wrong (the
   query, the URL, the validation, the audience) and encoded the developer's belief, bug included.
2. **It is one of three causes of similar size.** The test environment differs from production in 13
   bugs (permit-all security chain, no SES client, `@Transactional` without flush, test templates
   shadowing real ones). Production-shaped data was missing in 12 (unicode names, mixed case,
   namesakes, more rows than the limit). Cross-service contract drift accounts for 11, and those are
   almost all hidden by a mocked `UserApiClient`.
3. **The structural root:** the only layers that run real code end to end (Bruno, Playwright) run
   **after the deploy, against production**. That means they may not send mail or change real data,
   and #1208 explicitly skipped a Bruno test for that reason. Every check before production runs
   against mocks.
4. **Rewriting the 10,000 tests is not the answer.** Change the rules for new and touched tests,
   build four pieces of shared test infrastructure, and freeze the mock count so it can only go down.
5. **Epic 17: do part of this first.** The contract-test harness for service-to-service clients and
   the MSW harness for the frontend must exist **before 17.6–17.11**. Those stories move 12 S2S URLs
   and make breaking API changes. With today's tests, every such move stays green even when the
   caller is wrong. That is exactly #1072 and #1159/#1181, where the reminder job mailed 267 people.
   17.1–17.5 do not need to wait.

## What was measured (HEAD of `develop`, 2026-11-07)

| | Measured |
|---|---|
| Frontend test files | 441. **329** use `vi.mock`, 791 calls in total |
| ... that mock our own `@/services` | **177** files |
| ... that mock our own `@/hooks` | 112 files |
| ... that mock child components | 87 files (`EventPage.test.tsx` mocks 13 modules, every tab included) |
| ... that mock `react-i18next` per file | 115 (the mock dropped interpolation arguments, which hid #959) |
| ... that use MSW | **2**, although `msw` 2.15 is installed |
| ... fixtures typed via generated API types | 11 |
| EMS `@MockitoBean` declarations | 106 in 57 files, **38 of them `UserApiClient`** |
| EMS shared test config | `TestUserApiClientConfig` replaces the real CUMS client for **all** ITs |
| EMS ITs annotated `@Transactional` at class level | 85 (rollback without flush, so DB constraints and triggers do not fire) |
| Test security chain (EMS, SCS, ...) | `anyRequest().permitAll()` |
| Contract tooling (WireMock, Pact, Spring Cloud Contract) | none. `MockRestServiceServer` in 2 PCS client tests and the AI tests only |
| Bruno / Playwright | 296 `.bru`, 63 specs, run in `deploy-staging.yml` **after** the production deploy |

The guidance contradicts itself: `project-context.md:201` says "use MSW instead of mocking service
modules", and `:459` says "mock the service layer instead". `coding-standards.md` says nothing about
what may be mocked. Practice followed the easier of the two.

## Root causes by category (counts from the appendix; one bug can carry several labels)

| Code | Cause | Bugs | Typical case |
|---|---|---|---|
| E | No test for the path | 16 | #1159: 405/415 and missing parameters gave 500 |
| **A** | **Mock hid the defect** | **15** | #1176: `PartnerInviteEmailServiceTest:48` verified the joined To: line `eq("partner@example.com,organizer@batbern.ch")`, which the real `EmailService` rejects. Every partner invite failed and showed as sent |
| **C** | **Test environment ≠ production** | **13** | #1104: `SlugGenerationService` mocked in the CUMS config, plus `@Transactional` without flush. The `chk_username_format` constraint never ran ("Hông Ân" got a 500 six times) |
| G | Production data shape missing | 12 | #1034: `query: 'Anna'` is ASCII, so the double encoding of "Stürmer" stayed invisible |
| **H** | **Cross-service contract drift** | **11** | #1072: EMS sent `limit=1000`, which CUMS has rejected since #825. `getPartnerUsernames()` was mocked |
| F | Assertion vacuous | 11 | #1012: "contacts must be empty" held because the mock returned an empty list by default |
| I | Runtime / third party not represented | 9 | #1243: retired OpenAI model, fenced JSON |
| D | Units proven, wiring not | 8 | #962: the child tests passed the callback themselves, and the parent never did |
| L | Requirement gap (no test could catch it) | 6 | #1208: capacity raise promoted nobody |
| K | Failure swallowed into a log line | 5 | #1181: the reminder job always threw. The #1159 fix woke it up, and it mailed 267 people |
| J | Tests acted on production | 2 (+2 near misses) | #1069: a Bruno PUT replay nulled the evening start time |
| B | Fixture shape invented | 2 | #1106: fixtures carried an `id` the API never sends |

A, C, H and G together explain about two thirds of the bugs, and they share one mechanism: **the test
replaced the part of reality that held the defect**.

## Proposal

### P1. One mocking rule, written down: mock at the process boundary, nowhere else

> A test may replace only what leaves the process: SES, S3, Cognito, OpenAI, EventBridge/SQS, the
> clock, and **another service's HTTP API at the wire** (never its Java client). Everything inside
> the process runs for real: our services, repositories, mappers, validators, templates, the
> security filter chain, our hooks and our service modules.

| Layer | Allowed to replace | Not allowed |
|---|---|---|
| Backend IT | `SesClient`, `S3Client`, Cognito client, OpenAI HTTP, the peer service via `MockRestServiceServer` / WireMock | `@MockitoBean` of own services, repositories, `UserApiClient`, `EmailService`, `SlugGenerationService` |
| Backend unit | pure logic only; if a unit test needs more than 2 mocks, it is the wrong test level | `verify(mock).call(eq(<expected value>))` as the main assertion |
| Frontend component | the network via MSW; Amplify; browser APIs that jsdom lacks | `vi.mock('@/services/...')`, `vi.mock('@/hooks/...')`, `vi.mock` of child components of the screen under test |
| Frontend i18n | real i18next with the real `en` locale files | the per-file `react-i18next` stub |

Unit tests for pure functions stay as they are. The rule targets tests that claim to test behaviour
while the behaviour is mocked.

### P2. Four pieces of shared test infrastructure (the rule is only followed if it is easier)

1. **S2S contract harness (backend).** One test per client (`UserApiClientImpl`,
   `CompanyApiClient`, PCS clients, the CUMS→EMS pushes) runs the **real client** against
   `MockRestServiceServer`, with responses taken from the **provider's OpenAPI examples**. Every
   request is validated against the provider spec (path exists, parameters within limits). One
   provider-side test checks that the examples are valid responses of the real controller. The
   38 `@MockitoBean UserApiClient` then gradually become a WireMock stub fed by the same examples.
   Would have caught #1072, #1159/#1181, #1098 and #1125.
2. **MSW harness (frontend).** `src/test/msw/` holds default handlers for every operation in
   `docs/api/*.openapi.yml`, data built through **generated types**, and a request validator that
   fails the test when the frontend calls a path or parameter that is not in the spec. That is the
   unit-level twin of 17.3. Tests then render real screens with real hooks and real services. Would
   have caught #1034, #1106, #1207, #1116 and #962.
3. **Production-shaped fixtures.** One shared fixture set per side, used by default: `Hông Ân`,
   `O’Neill`, `王`, mixed-case emails, IDN domains, two namesakes, more rows than the page limit,
   ties in the sort key, past and future events, every registration status. A builder that produces
   ASCII-only data is the exception, not the default.
4. **Production setup in tests.**
   - (a) Every service runs its **production** security chain in a guard test, as the gateway already
     does with `ProductionSecurityFilterChainTest`.
   - (b) `EmailService` always runs for real against a mocked `SesClient`, in tests and in local dev.
     Validation must not sit behind `if (sesClient == null)`.
   - (c) Write paths with DB constraints get a non-`@Transactional` IT, or `flush()` before the
     assertion.
   - (d) Cache tests read, mutate, then read again.
   - (e) No test copies of production resources (templates, configs) on the test classpath.

### P3. Assertion rules (cheap, in the review checklist and in `coding-standards.md`)

- **Asserting absence needs a positive control** in the same test: first show that the thing would
  be there (#1012, #960).
- **Assert the effect, not the call.** Check the parsed MIME `To:` or the stored row, not
  `verify(times(2))` (#1176, #1181).
- **A fallback must be distinguishable from success** in the test data (#1243).
- **No silent catch in `@Async`, schedulers or listeners** without a metric. The test asserts the
  failure surfaces (#1176, #1181, #1145). An ArchUnit rule can flag catch blocks that only log.
- **Wiring test at the parent** for every callback prop that triggers a mutation (#962).

### P4. Ratchet instead of rewrite

10,000 tests are not rewritten. Instead:

- **Freeze the counts**, the same way 17.5 freezes ArchUnit violations:
  - `vi.mock('@/services|@/hooks|child component')` per file;
  - `@MockitoBean` of in-process beans and of `UserApiClient`;
  - class-level `@Transactional` on write ITs.

  A CI check fails when a count rises. It passes when a count falls, and the baseline file is
  updated in the same PR.
- **Touch rule:** a story that changes a screen or a service method moves that screen's or method's
  tests to the harness. No separate rewrite stories, except for the top 10 offenders, which get
  one story.
- Remove the contradiction in `project-context.md` (`:459`) and add P1–P3 to `coding-standards.md`
  and to Amelia's dev workflow, which is where the tests are actually written.

### P5. A real-code gate before production (owner decision, bigger)

> **Parked 2026-11-07 (owner):** continues after 17.1–17.5 and 17.0a–d, local spike on rack first.
> Detailed plan: [`pre-deploy-gate-plan.md`](pre-deploy-gate-plan.md).

Today the first end-to-end run of real code is on production, so it cannot exercise mail or
destructive flows (#1208), and it has damaged data (#1069, 6,365 orphan notifications). There are
two options:

| Option | What | Cost | Catches |
|---|---|---|---|
| a | Bruno in `build.yml`, before deploy, against the **production images** the build already produces for ECR, started with a Postgres container, MinIO and a local SES fake. Production profile with only the external endpoints overridden. Tokens from the CI Cognito secrets as today | One CI job. The ~15–25 min wall time is an estimate, not measured. A spike first: what the production profile needs at startup (SQS, Secrets Manager, ...) | Contract drift, security chain, cache, mail-sending flows without real mail, and the shipped artifact itself. Bruno can then test what it must never run in production |
| b | Status quo: post-deploy only, plus P1–P4 | none | P1–P4 cover most of A/C/H/G at unit/IT level; send flows stay untested end to end |

I recommend **a**, but after P2.1/P2.2, because the harnesses give more per hour. It is your
decision: it adds pipeline time to every PR.

**Not `docker-compose.yml`, and not `make dev-native-up` either** (corrected 2026-11-07, checked in
the files). Both run the `local` profile, which is exactly where the C-class divergences live
(`NoOpCacheManager`, no `SesClient`). `docker-compose.yml` does not even run the shipped artifact: it
builds `Dockerfile.dev` and runs `gradlew bootRun` on the mounted source, and it has no Postgres
service. It is the older form of the same developer stack. The native stack (fat JARs, local
Postgres, MinIO, `SPRING_PROFILES_ACTIVE=local`) replaced it for its 60–70 % lower resource use, and
stays the right tool for people. For a gate, the question is not Docker or native. The question is
which artifact and which profile, and the answer is the production image with the production
profile.

## Sequencing with Epic 17

| Order | Item | Why here |
|---|---|---|
| now | 17.1, 17.2 (deletions), 17.3, 17.4, 17.5 | They delete code or add checks. Mocked tests cannot hide a regression there, and 17.3/17.4 are themselves contract guards |
| **before 17.6** | **P1 rule + P4 freeze**, **P2.1 S2S contract harness**, P2.4a/b | 17.6–17.8 move 12 S2S URLs and change who calls whom. Without P2.1, a wrong `/internal` path stays green, the #1159 class |
| **before 17.9** | **P2.2 MSW harness** (+ P2.3 fixtures) | 17.9–17.11 are breaking API changes "with the frontend in the same story". With `vi.mock('@/services')` the frontend tests cannot notice a wrong path or shape |
| after 17.11 | P5a | It needs the harnesses to be cheap to keep green |

Effort is a rough estimate, not measured. P1+P3+P4 take about 1 story, including the freeze check.
P2.1 takes 1–2 stories (start with `UserApiClientImpl`, the 38 mocks). P2.2 takes 2 stories
(handler generator + validator, then the top 10 files). P2.3/P2.4 take 1 story. That is about 5–6
stories, of which roughly 3 must precede 17.6.

Packaging: either as stories 17.0a–d at the head of Epic 17, or as a small separate Epic 18 "Test
fidelity" with 17.6 and 17.9 depending on it. I prefer **17.0a–d**. The dependency is then visible
in one table, and John's order stays the single source of truth.

## Not done

- No tests or builds were run. All counts are greps of `develop` HEAD, and the bug causes come from
  PR bodies and pre-fix code.
- 15 of the 57 classifications are inferred, not verified in code (marked `I` in the appendix).
- CI/ops-only fixes (backups, deploy skip, Dependabot) are left out.
- The 15–25 min for P5a is a guess. Measure it before deciding.

## Appendix: bug forensics

The full table with file:line evidence and the cheapest catching test per bug follows.


Source: PR bodies, plus `git show <squash-merge>^:<path>` for the tests that existed before each fix.
V = verified from PR text or code before the fix. I = inferred (reasoned, not checked in code).

Taxonomy: A mocked collaborator hid it · B fixture shape differs from real API/DB · C test env differs from prod · D unit tested, wiring not · E no test for the path · F assertion too weak or vacuous · G production data shape/edge case missing · H cross-service contract drift · I third-party/runtime behaviour not represented.
Added: **J** the test suite itself acted on production (caused or nearly caused the defect) · **K** failure swallowed or downgraded to a log line, so "no exception" tests and monitoring stayed green · **L** requirement/design gap: the code did what the story said, so no test could have caught it.

| # | PR / issue | Symptom in production | Layer | Why missed | V/I | Evidence | Cheapest test that would have caught it |
|---|---|---|---|---|---|---|---|
| 1 | #874 (be2498e4) | Cold deep link to /login or /register crashed into ErrorBoundary while runtime config was loading | FE wiring | C, D | I | `useFeature` used the throwing `useConfig()`. Inferred: unit tests render inside a provider whose config is already resolved | Component test that renders the route with a pending config promise |
| 2 | #960 / #903 | `GET /events?sort=date%` → 500 CRITICAL (ZAP re-filed it weekly) | BE shared-kernel parse | E, I | V | Spring Data `checkSortExpression` throws `InvalidDataAccessApiUsageException`, not the `PropertyReferenceException` the handler maps to 400. Only "unknown field" was tested | Parameterised IT over punctuation in `sort` (fuzz class) |
| 3 | #962 / #821 | "Active partnership" toggle did nothing | FE wiring/parent | D | V | PR: "`PartnerSettingsTab`'s own tests passed `onUpdateStatus` **themselves** … the component was proven, the wiring never was". Parent rendered `<PartnerSettingsTab partner={partner} currentUser={currentUser} />` | Parent-screen test asserting the HTTP call (MSW) |
| 4 | #962 | Auto-renewal switch and renewal date bound to fields in neither `PartnerResponse` nor `UpdatePartnerRequest`; always undefined | FE ↔ API mapping | B, H | V | PR: fields "exist in neither … and never have" | Props typed from generated API types (tsc fails); fixtures from generated types |
| 5 | #1012 / #961 | Partner POST/PATCH/DELETE, statistics, contacts open to any logged-in user (attendee could deactivate a sponsor) | BE controller/security | C, F | V | PR: "The `test` profile replaces the filter chain with `permitAll`"; "Ten existing tests exercised the mutation endpoints with **no authenticated user** and passed only because of the defect" | Role-matrix test per endpoint (anonymous/attendee/organizer) against the real chain; `EndpointAuthorizationGuardTest` (now exists) |
| 6 | #1012 / #961 | Anonymous `GET /partners?include=contacts` returned contact emails and names | BE controller | A, F, C | V | PR: "the mocked user service returns an empty list by default, so 'contacts must be empty' held whether or not the leak existed" (fixed with `stubOneContactFor()`) | Anonymous IT with a stubbed non-empty contact; anonymous probe (now `access-control-check.py`) |
| 7 | #1012 / #959 | Public archive showed hardcoded English "N of M events" to German visitors | FE component | A, F | V | `ArchivePage.test.tsx:34` mock `t: (key: string)` dropped the interpolation argument; E2E comment called the string "hardcoded (not localized) — safe to assert its shape" | Test with a real i18next instance in `de`; lint for JSX literals |
| 8 | #1014 / #662 | Abstracts over 1000 chars refused; batch import had no limit | Spec / BE validation | L, F | V | Tests asserted only the rejection at 1001; "added the **positive** case that was missing — 1200 characters must now *save*" | Positive realistic-length case; cross-spec consistency check |
| 9 | #1014 / #823 | Validation error rendered below the fold; Save looked like a no-op | FE component | I | V | jsdom has no layout and no `scrollTo` ("calling it unguarded threw `scrollTo is not a function`") | Playwright `toBeInViewport()` after failed save |
| 10 | #1014 / #818 | Subscribing on the public widget not reflected on My Profile for 5 min; widget shown to subscribers | FE hooks wiring | D, E | V | "`useNewsletterSubscribe` mutation had **no `onSuccess` at all**"; hooks tested one by one with mocked services | Two hooks on one QueryClient + MSW: mutate, then assert the other query refetched |
| 11 | #1034 (1) | User search capped at 20 with no ORDER BY; target ranked 29 of 32 "Matthias" was unreachable | BE service + persistence | A, G | V | `UserSearchServiceTest`: `@Mock UserRepository` (cap and ordering live in the mocked part); fixtures of a handful of users | Testcontainers repository test with >20 namesakes |
| 12 | #1034 (2) | Double URL-encoding: every query with a space or umlaut returned 0 ("Stürmer") | FE service | A, G | V | `userManagementApi.test.ts:300` `mock.onGet('/users/search', { params: { query: 'Anna', limit: 10 } })`: ASCII-only, and axios-mock-adapter compares the params object, not the wire URL | Test with `'Stürmer'` asserting the serialised URL; MSW (sees the real URL) |
| 13 | #1034 (3,5) | Role filter applied client-side after truncation; picker showed username instead of name | FE component | E | V | "`UserAutocomplete` had **no test file at all**" | Component test |
| 14 | #1034 (4) | Prefill silently pre-selected the wrong "Matthias" (`users[0]`), promote would grant SPEAKER to the wrong person | FE component | G, F | I | Fixtures had unique names; tests asserted "a user is selected", not which | Test with two namesakes asserting no auto-selection |
| 15 | #1069 (1) | Public site showed READY/INVITED (unconfirmed) speakers after SPEAKERS phase auto-published | BE service | L, H | I | Story 11.E.8 made sessions exist from READY on; public-read tests predate it and seeded only accepted speakers | Visibility matrix IT (phase × speaker state), now `PublicSessionVisibilityIntegrationTest` |
| 16 | #1069 (2) | Organizer could no longer change the event state (only button removed with the Overview tab in #788) | FE | E | I | Removing the tab removed its tests; nothing asserted the capability is reachable | E2E "organizer can override state" |
| 17 | #1069 (3) | Evening agenda started at 09:00: Bruno 04-06 PUT a 7-field capture back on every prod deploy, nulling `typicalStartTime` | Bruno test → BE data | J, F | V | PR: "captured 7 fields of the EVENING template and PUT it back without `typicalStartTime`" | Required field in spec; Bruno round-trip assertion; no PUT-replay against prod |
| 18 | #1069 (4) | New sessions missing from agenda after promote (15-min server cache not evicted) | BE cache | C, E | V/I | `CacheConfig`: `@Profile("local") NoOpCacheManager` so local checks never see staleness (V); ITs never primed the cache before the mutation (I) | IT: read → promote → read with Caffeine active |
| 19 | #1072 (1) | New events created without partners: EMS asked CUMS `limit=1000`, CUMS capped at 100 since #825 → 400 | Cross-service | H, A | V | `UserApiClientImpl.java:782` `.queryParam("limit", 1000)`; `EnrollStakeholdersTest:91` `when(userApiClient.getPartnerUsernames()).thenReturn(List.of("partner.one"))`; EMS has 37 `@MockitoBean UserApiClient` | Client test against `MockRestServiceServer`/WireMock validated by the CUMS OpenAPI (contract test) |
| 20 | #1095 | 4th BATbern60 speaker blocked ("max 3"); slot readers ignored per-event override; backend counted only ACCEPTED | BE service | E, D | I | Story 15.2 added the override; the five readers were not audited; tests seeded templates only and never read the cached summary first | IT with an override row, after a cached read |
| 21 | #1096 | `.ics` sent with the "please confirm" mail; ~30 attendees did not confirm and lost the seat | Email template | L, C | V | Stale `src/test/resources/email-templates/registration-confirmation-*.html` "shadowed the real templates on the test classpath, so no test ever exercised the production templates" | Golden test against main-classpath templates (now `MailGoldenTest`) |
| 22 | #1098 | Registering with another user's additional email → 500 (DB trigger) | BE service / DB | E, H | V | Guard + test existed only for JIT (`JITAdditionalEmailGuardIntegrationTest`); `get-or-create` had none; EMS side mocks `UserApiClient` | CUMS Testcontainers IT: get-or-create with an existing additional email |
| 23 | #1104 / #1102 | "Hông Ân" → `hong.an.meier` → `chk_username_format` 500, six attempts | BE service / DB constraint | A, C, G | V | `TestAwsConfig.java:164-173` `@Primary Mockito.mock(SlugGenerationService.class)` with a `thenAnswer` re-implementation; PR: "`@Transactional` tests never flushed, so the constraint never ran" | Testcontainers IT with the real generator, explicit flush, unicode name table |
| 24 | #1104 | Four username generators (JIT, Lambda, reconciliation, get-or-create) disagreed; reconciliation's `user.<ts>` always violated the constraint | Cross-component | H, E | V | PR: "four generators disagreed"; now pinned by golden `username-generation-cases.json` | Shared golden file both implementations run |
| 25 | #1104 | Sign-up rejected `O’Neill`, NFD Vietnamese, single-char and Devanagari names, IDN email domains | FE validation | G | V | PR lists each case; zod `.email()` rejected IDN | Table-driven validation test with real-world names |
| 26 | #1106 | Every speaker card showed "Needs a slot" | FE component ↔ API | B, H | V | PR: "The test fixtures had given sessions an `id` the API never sends"; #825 mapper stopped passing `id` | Fixtures typed from generated types or recorded responses |
| 27 | #1107 / #1099 | Case-sensitive email matching: 5 duplicate users, double newsletters, missed unsubscribes and bounces | BE persistence | G, E | I | Fixtures lowercase; 58 + 50 prod rows stored with capitals | IT with mixed-case input on every email finder |
| 28 | #1109 | Homepage photo row vanished (top-5 events by date were future events) | BE persistence query | A, G | V | `EventPhotoServiceTest`: `when(eventRepository.findAllByOrderByDateDesc(any())).thenReturn(recentEvents)`: the mock supplied exactly "the right events", the selection was the bug | Testcontainers test with future events (now `EventPhotoServiceRecentPhotosIntegrationTest`) |
| 29 | #1116 / #1112 | Presenter agenda slide empty on event screen (anonymous `/events/{code}` lost sessions in #1072) | FE service ↔ BE contract | H, A, F | V | `presentationService.test.ts` pinned `toHaveBeenCalledWith('/events/BATbern142', …)` on a mocked apiClient; `@gate` E2E only asserted the agenda container visible (row assertion added in the fix) | E2E asserting `agenda-session-row`; anonymous contract test |
| 30 | #1116 / #1068 | 6,365 orphan test notifications in prod for the test organizer | Test residue | J | V | Notifications had no FK to events; nightly E2E against prod left them | Teardown sweep (now added) |
| 31 | #1118 / #1117 | Presenter printed "Pause" for every break/lunch/apéro | FE component | E | I | `AgendaView.test.tsx` "New" per PR | Component test |
| 32 | #1125 / #1123 | Apple Watch 2.3 could not load any event with an apéro; `materialsStatus` had two rules (PARTIAL vs COMPLETE) | Cross-service contract | H | V | Story 15.2 added `aperitif`; #815 relaxed the spec enum to a free string to match the drift; watch decodes a closed enum | Spec enum + backward-compat diff in CI; consumer decode test of prod payload |
| 33 | #1134 | Registrations list, distribution lists, admin settings readable anonymously | BE security | C, E | V | PR: "The production chain (`@Profile("!test")`) had no test at all, since every other gateway test runs the permit-all test chain"; no `@PreAuthorize` | `ProductionSecurityFilterChainTest` (now exists) |
| 34 | #1145 / #1141 | "Resend confirmation code" showed success and sent nothing (8× in 30 days); UNCONFIRMED users dead-ended on every screen | Cognito Lambda + FE auth | K, I, E | V | Lambda logged "not implemented yet" at INFO; `USER_NOT_CONFIRMED` unmapped in FE | Handler test per trigger source; FE test with Cognito `UserNotConfirmedException` |
| 35 | #1145 | Any user could PATCH/DELETE a registration | BE security | C, E | V | No `@PreAuthorize`; test chain permitAll | Role-matrix IT |
| 36 | #1150 | Badge "Ausgebucht — Warteliste" while hero said "Sie sind angemeldet"; `[MISSING]` capacity texts in 8 locales | FE components | D, A | I | Two components each derived status (unified in #1241); per-file `vi.mock('react-i18next')` (115 files) returns keys, so missing translations never render in tests | Page-level test; locale completeness check |
| 37 | #1154 / #1129 | Newsletter send skipped 13 of 1060 (offset paging while people unsubscribe) | BE async | G, C | I | Static fixtures; nothing mutates between pages | IT that unsubscribes between pages |
| 38 | #1154 / #1131 | Unstable paging (no tie-breaker) at 15 sites | BE persistence | G | I | Small fixtures with distinct sort keys; ties are where Postgres order is undefined | Guard test (now `DeterministicPagingGuardTest`) |
| 39 | #1154 / #1132 | Client IP taken from spoofable first `X-Forwarded-For` hop (rate limit, Turnstile, CUMS internal auth) | BE security / infra | I, C | V | Measured on prod; tests never modelled the real proxy chain | Unit test with a spoofed hop chain |
| 40 | #1156 / #1155 | Public responses leaked `organizerUsername`, `updatedBy`, organizer emails, partnership end date, registrant email | BE projection | F, E | I | Tests assert presence of fields, never absence for anonymous callers | Anonymous-response field allowlist test |
| 41 | #1159 / #1148 #1157 #1103 | Missing param, 405, 415, NUL byte → 500 CRITICAL (94 on one ZAP run) | BE handlers | E, I | V | Catch-all handlers; only happy and validation paths tested | `ClientErrorContract` through the real resolver chain (now exists) |
| 42 | #1159 / #1142 | `/users/me?include=company` 500 for 68 users with dangling `company_id` (rollback-only) | BE service | G, C | I | Fixtures have consistent FKs; rollback-only surfaces only at a real tx boundary | IT with a dangling FK, non-`@Transactional` |
| 43 | #1159 / #1146 + #1181 | EMS called non-existent CUMS `/users/{u}/preferences`; every reminder send threw | Cross-service | H, A, K | V | `UserApiClientImpl.java:269` `"/api/v1/users/" + username + "/preferences"`; consumers mock `UserApiClient` | Contract test of the client against the CUMS spec |
| 44 | #1181 / #1179 | `DeadlineReminderJob` mailed all 267 BATbern60 registrations incl. cancelled and waitlisted, once #1159 fixed the URL | BE scheduler | A, F, K | V | `DeadlineReminderJobTest:75` `when(registrationRepository.findUsernamesByEventCode("BATbern123"))…` + `verify(notificationService, times(2))`: audience came from a mock, assertion counts calls. Query had no status filter | Testcontainers test with mixed-status registrations asserting recipients; `AutomaticTriggerAudienceGuardTest` (now exists) |
| 45 | #1176 / #1175 | BATbern60 partner meeting invite to 20 never sent; UI said "sent" | BE email + async | A, C, K | V | `PartnerInviteEmailServiceTest:48` `eq("partner@example.com,organizer@batbern.ch")`: the mock pinned the joined string as correct; `EmailService` `if (sesClient == null)` branch locally; `@Async` caught and logged; `invite_sent_at` written before send | Real `EmailService` against a mocked `SesClient` (now added) |
| 46 | #1185 / #1183 | Logged-in non-registrant offered "Anmeldung stornieren"; dialog promised a mail that could never come | FE | L, D | I | Cancel link and registration state rendered independently | Component test per auth × registration state |
| 47 | #1196 / #1195 | SES `AMAZON_SES_SETUP_NOTIFICATION` forwarded to 11 organizers | Async / SES | I | V | SES writes a test object on every receipt-rule change | Listener test with that key |
| 48 | #1200 / #1198 | Organizer nav wider than window 1200–1440 px | FE layout | I | I | jsdom has no layout | Playwright at breakpoints |
| 49 | #1203 / #1184 | Email addresses in prod logs via framework DEBUG in base `application.yml` | Config | C, E | V | Prod runs base + `staging`; tests run `test` profile | Config guard (now in `LogAddressGuardTest`) |
| 50 | #1205 | Homepage prerender silently failed on every deploy since #1072 | Build / FE | H, K | V | Crawl matched only `/api/v1/events/current`; failure logged as a warning, CSR fallback shipped | Build step fails on prerender failure |
| 51 | #1206 | Merge dialog reloaded the deleted profile (404 per merge); messages "User with ID 'User not found: x' not found" | FE wiring / BE | D, F | V | "Test first: one preview call; before the fix there were two" | Test asserting call count after mutation |
| 52 | #1207 | Email templates never saved: composer Save disabled for non-kebab names, admin modal sent "Partner Info" → 400 | FE ↔ BE validation | A, G, H | V | `EmailTemplateEditModal.test.tsx`: `mockCreateMutateAsync.mockResolvedValue(...)` accepts any key; all fixtures use kebab keys (`'venue-timetable'`) | MSW handler that enforces the spec `pattern`; test typing a human name |
| 53 | #1208 | Raising capacity 220→250 promoted none of 42 waiting | BE service | L | I | Promotion was only specified for cancellations | n/a (requirement) |
| 54 | #1240 / #1235 | Raw `{{#eventCode}}` tags in Mail Center notices | Email rendering | A, G | V | PR: "4 EMS tests whose stand-in renderer did not handle sections"; the no-event variable set was not a fixture | Golden test of the real renderer with the no-event variable set |
| 55 | #1241 / #1238 | Registration offered a day after the deadline; status and numbers shown differently in several places | FE logic duplicated | D, L | V | PR: one function now decides ("one definition of registration open (G9)"); fixture date 2026-09-15 had become a time bomb | Table test of the single decision function (now 80 rows) |
| 56 | #1243 | "Generate with AI" theme image failed: `gpt-image-1` retired | Third-party | I | V | OpenAI 400 "model does not exist" | Post-deploy smoke of the AI call; alias config |
| 57 | #1243 | Trending topics fell back to the fixed list 7×/week (JSON wrapped in a Markdown fence) | BE AI | A, F, I | V | `TopicSessionDataControllerIntegrationTest:59` `@MockitoBean TrendingTopicsService` returns `List.of("AI Agents", …)` and asserts `hasItem("AI Agents")`, which is also `FALLBACK_TOPICS[0]`: happy path and fallback are indistinguishable | Parser test fed a fenced reply (recorded real response) |

## Test-infrastructure defects found alongside (not production bugs, but they explain misses)

- #1200 / #1186: EMS `TestAwsConfig` S3 mocks had the production bean names; with overriding enabled the **real** SDK clients won. (C, V)
- #960 / #955: `auth-config-race` asserted `getByRole('heading',{name:'Welcome Back'}).toHaveCount(0)`; under a German UI it passed vacuously. (F, V)
- #1036: Bruno users-api fell back to `testUsername: batbern.organizer` when setup failed, stripping ORGANIZER from the real account in a demonstration run. (J, V)
- #1154 / #1135: `venue-coordination-preview.bru` PUT test contacts into the live venue setting. (J, V)
- #1069, #1241: fixed-date fixtures (2026-09-10, 2026-09-15) turned into failing tests once the date passed.

## Counts (57 instances, 41 verified, 15 inferred, 1 mixed; multi-label)

E no test 16 · A mock hid it 15 · C env divergence 13 · G prod data shape 12 · H contract drift 11 · F weak assertion 11 · I runtime/third-party 9 · D wiring 8 · L requirement gap 6 · K swallowed failure 5 · J tests acting on prod 2 (+2 near misses) · B fixture shape 2 (B overlaps heavily with A and G in practice)

