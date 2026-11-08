---
title: 'S2S contract harness on the public-API clients (17.0b1)'
type: 'feature'
ticket: '0b1'
created: '2026-11-08'
status: 'built'
baseline_revision: '8671ced678e8ebfa56f8e9180ba717bbc93ae67a'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/plans/test-fidelity-proposal-2026-11.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Our service-to-service clients are tested against Mockito mocks of their own interface or of `RestTemplate`, so a request the provider rejects stays green (#1072: EMS sent `limit=1000`, CUMS allows 100). No test checks a client against the provider's OpenAPI.

**Approach:** A shared harness in `shared-kernel` test fixtures binds `MockRestServiceServer` to the client's real `RestTemplate`, validates every request against the provider's spec in `docs/api/` and answers with the spec's example (or a test body validated against the response schema). Every public `/api/v1` S2S call gets a contract test on it, each provider proves its examples match its real controller, and the own-code mocks of fully public clients move onto the harness.

## Boundaries & Constraints

**Always:** the real client class runs, unchanged in behaviour; a request outside the spec (unknown path, parameter over its maximum, missing required field) fails the test with the validator's message; responses come from the spec, so a spec without a usable example gets one added (no API change, regenerate types if the generator output changes); tests follow the Test fidelity rules; ratchet ends lower than it starts.

**Never:** no `/internal` calls (0b2 owns their specs and tests); no WireMock or Pact; no real network; no change to any endpoint's behaviour; no `@MockitoBean` of a client the harness can cover.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| valid call | `getAllCompanies()` pages with `limit=100` | client gets the spec example, maps it | none |
| #1072 fixture | a request to `GET /api/v1/companies?limit=1000` through the harness | test fails | validator message names `limit` and the maximum 100 |
| unknown path | request to a path the spec lacks | test fails | message names method and path |
| custom body | IT supplies its own response body | body validated against the response schema first | schema mismatch fails the test |
| provider check | each consumed operation called on the real controller | response valid against its spec, and the spec example valid against its schema | mismatch fails the provider test |

</frozen-after-approval>

## Code Map

All clients use `RestTemplate` (no `RestClient`/`WebClient`). There is no `CompanyApiClient` class: company calls live in EMS `UserApiClientImpl` and PCS `CompanyServiceClientImpl`.

- `shared-kernel/build.gradle` -- `java-test-fixtures` already applied; holds `AbstractIntegrationTest`, `EndpointAuthorizationGuard`. Harness goes in `src/testFixtures/java/ch/batbern/shared/test/contract/`.
- EMS `client/impl/UserApiClientImpl.java` -- public calls: `POST /users/provision` (455), `PATCH /users/{u}/profile` (530), `POST /users/{u}/issue-invitation-credentials` (598), `GET /companies` paged (790, `MAX_PAGE_SIZE` 100 since #1072 fix `83f276aa`), `GET /companies/{slug}` (871). JWT header via `createHeadersWithJwtToken`. RestTemplate bean: EMS `config/RestClientConfig.java:40`.
- EMS `client/impl/PartnerApiClientImpl.java` -- `GET /partners/topics` (49), `GET /partners?include=company&size=200` (108); no tests today. partners-api `size` has no maximum: read the PCS controller's real limit and put it in the spec; if PCS rejects or truncates 200, fix the client.
- PCS `client/impl/CompanyServiceClientImpl.java` (`GET /companies/{name}`), `UserServiceClientImpl.java` (`GET /users/me`, line 115; the rest is `/internal`), `EventManagementClientImpl.java` (`GET /events/attendance-summary`, `GET /events/{code}?include=sessions`); RestTemplate bean `config/RestClientConfig.java:40`.
- Existing tests to move onto the harness: PCS `client/UserServiceClientTest`, `CompanyServiceClientTest` (MockRestServiceServer, no spec); EMS `UserApiClientImpl{GetOrCreateUser,PagedLists,InternalApi,GetCompanyDisplayName}Test` (`@Mock RestTemplate`): their public-call cases move, `/internal` cases stay for 0b2.
- Specs without response-level examples for consumed ops: users-api `provisionUserWithRole`, `patchUserProfile`; partners-api `listTopics`, `listPartners`; event-analytics-api `getAttendanceSummary`.
- Mocks to retire (ratchet `be-mock-s2s-client`, baseline 63): PCS `@MockitoBean CompanyServiceClient` 4, `EventManagementClient` 5; EMS `PartnerApiClient` 1. `UserApiClient` (38) and `UserServiceClient` (10) stay: mostly `/internal`, 0b2.
- `services/event-management-service/src/test/java/ch/batbern/events/config/TestUserApiClientConfig.java` -- `@Primary Mockito.mock(UserApiClient)`, 11 users, invisible to the ratchet (counts only `@MockitoBean`).
- `web-frontend/scripts/test-ratchet.ts` -- `mockitoBeanTypes` (240), category rule (327).
- EMS `client/impl/PartnerMeetingRsvpClientImpl.java:31` -- reads undefined `partner-coordination-service.base-url`; works in production only because both defaults agree, breaks on local dev (`start-all-native.sh:277` sets the env for `partner-service.base-url` only).

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/build.gradle` + `src/testFixtures/.../contract/` -- add the request validator (first candidate `com.atlassian.oai:swagger-request-validator-core`; check Jackson-2 compatibility; record version) and the harness: bind to a `RestTemplate`, expect by method + path, validate request, respond with the operation's example or a schema-validated body -- one reusable tool, so tests use it instead of mocks.
- [x] same -- harness self-test: the #1072 fixture (`limit=1000` fails naming the maximum), unknown path, custom body failing its schema.
- [x] `docs/api/users-api`, `partners-api`, `event-analytics-api` `.openapi.yml` -- add the missing examples; partners `size` maximum per the PCS controller; regenerate types if output changes.
- [x] EMS + PCS client contract tests -- one per client covering every public call above; move the public cases out of the `@Mock RestTemplate` tests and the two PCS MockRestServiceServer tests.
- [x] provider tests in CUMS, EMS, PCS -- each consumed operation called through MockMvc on the real controller, response validated against its spec, examples validated against their schemas.
- [x] PCS + EMS ITs -- replace `@MockitoBean CompanyServiceClient`, `EventManagementClient`, `PartnerApiClient` with the harness; `test-ratchet.ts` also counts `mock(XClient.class)` in test sources so `TestUserApiClientConfig` is visible; `--update` the baseline.
- [x] `PartnerMeetingRsvpClientImpl` -- use `partner-service.base-url`; test that it hits the configured URL.
- [x] `docs/architecture/coding-standards.md` Test fidelity rules -- one paragraph: how to use the harness; record ratchet start/end in Implementation Notes.

**Acceptance Criteria:**
- Given the #1072 fixture, when its test runs, then it fails with the validator's message naming `limit`.
- Given every public `/api/v1` S2S call listed in the Code Map, when the contract tests run, then each has a passing test on the harness.
- Given the branch, when `make check-test-ratchet` runs, then `be-mock-s2s-client` is lower than 63 by at least the 10 retired mocks, net of newly visible `mock(...)` lines, with no RAISED line.

## Design Notes

Architecture note: modules: shared-kernel test fixtures, EMS/PCS `client`, CUMS/EMS/PCS tests · new dependencies: one OpenAPI validator, test scope only · endpoints: none (spec examples and one `maximum` added) · reused: `MockRestServiceServer`, existing `RestTemplate` beans, `docs/api` specs · deviations: none.

Golden example:

```java
var server = ContractServer.bindTo(restTemplate, "docs/api/companies-api.openapi.yml");
server.expect(GET, "/api/v1/companies").respondWithExample();
var companies = client.getAllCompanies();   // request validated, example returned
```

## Verification

Rack rules: one Gradle job at a time, `--max-workers=4`, logs via `tee` to `~/.cache/batbern-test-logs/`, memory check before each run (stop above 12 GB).

**Commands:**
- `./gradlew :shared-kernel:test --max-workers=4` -- expected: harness self-tests pass.
- `./gradlew :services:partner-coordination-service:test --max-workers=4`, then the same for EMS and CUMS -- expected: pass.
- `cd web-frontend && npx vitest run scripts/test-ratchet.test.ts && cd .. && make check-test-ratchet` -- expected: OK, no RAISED.
- `cd web-frontend && npm run check:api-types` -- expected: generated types match specs.

## Implementation Notes

Implemented 2026-11-08 on `feature/17-0b-s2s-contract-harness`, uncommitted (no commit asked for).

**Validator.** `com.atlassian.oai:openapi-request-validator-core:3.0.0` (the renamed
swagger-request-validator-core; Atlassian relocated the artifact at 3.0.0, published 2026-05-21).
Built on Jackson 2.21 and swagger-parser 2.1.41; resolves to Jackson 2.21.7 under the Boot BOM, the
line the services run on (`spring-boot-jackson2`). Test fixtures only (`testFixturesApi`), plus
`javax.xml.bind:jaxb-api:2.3.1` at test-fixture runtime: swagger-core (non-jakarta) reads
`javax.xml.bind` annotations while parsing 3.1 specs, and Boot manages `jakarta.xml.bind-api` to 4.x,
which no longer has them (CUMS failed with `NoClassDefFoundError` without it).

**Harness** (`shared-kernel/src/testFixtures/java/ch/batbern/shared/test/contract/`):
`OpenApiContract` (spec loaded once per JVM, request/response validation, examples, path matching),
`ContractServer` (MockRestServiceServer on the real `RestTemplate`, unordered, `once()` by default,
`close()` restores the real request factory for shared Spring contexts), `ProviderContract`
(real MockMvc response and examples against the schema), `GeneratedCodeSemantics` (see below).
Two library gaps closed in the harness: the validator checks only operation-level `security`, so
root-level `security` (most of our specs) is checked by `OpenApiContract`; unknown query parameters
are raised from the library default IGNORE to ERROR. `withResolveCombinators(true)` is needed for
`allOf` schemas (EventDetail), otherwise every property of the other parts is rejected.

**GeneratedCodeSemantics** (deliberate, documented leniency, read the spec as the generated code does):
1. `nullable: true` in a 3.1 spec admits `null` (strict 3.1 ignores the keyword; openapi-generator
   and openapi-typescript honour it).
2. A non-required property may be `null`. Measured: the CUMS provider test showed `UserResponse`
   sends `"bio": null`, `"company": null`, ... for every unset optional field; every generated Java
   model reads optionals as `@Nullable`. EMS also sends `"firstName": null` / `"profilePictureUrl": null`
   in provision and patch requests, which CUMS accepts. Required properties stay non-null.
3. `format: time` means `HH:mm[:ss]`: EMS sends `"typicalStartTime": "18:00"`, every example uses
   `'16:00'`, the generator maps it to `String`; RFC 3339 `time` is produced by nothing here.

**Findings the harness surfaced (checked by running the tests):**
- users-api `UserResponse.required` listed `isActive`, a property that does not exist (the field is
  `active`); every real `/users/me` and profile response failed it. Removed from `required`; no
  generated Java or TS output changes.
- users-api `issue-invitation-credentials` example `temporaryPassword: null` was invalid under strict
  3.1 (rule 1 above).
- events-core `GET /events/{eventCode}` examples: `workflowState: PUBLISHED` (not in the enum, now
  `AGENDA_PUBLISHED`) and unquoted `2025-05-15 09:00:00+00:00` dates, which the parser serialises as
  `"2025-05-15 09:00:00Z"`; PCS's real client then failed to parse `Instant`. Quoted ISO now.
- Frontend type mismatch, not fixed here: `user-api.types.ts` declares optional fields as
  `bio?: string`, while CUMS sends `null`. Belongs to the MSW harness (17.0c).
- PCS `GET /partners/topics` looks every caller up in CUMS via `/internal/users/{username}`, organizers
  included; the PCS provider test answers it with `expectInternalWithoutSpec` (no validation, `/internal`
  paths only) until 17.0b2 specs it.
- EMS `GET /events/{code}?include=sessions` returns sessions only to organizers (existing
  `PublicSessionVisibilityIntegrationTest`), so PCS's ICS times fall back to the event-type default for
  any non-organizer caller. Not changed; noted for the owner.

**partners-api `size`.** Deviation from the plan, on purpose: PCS has no limit. `PartnerController`
passes `size` to `PartnerService.listPartners`, which ignores page and size and returns every partner
(read 2026-11-08). Adding a `maximum` would make the generated controller reject larger values with
400, an endpoint behaviour change the plan forbids. The spec now documents "no maximum, never
truncated" in the parameter description instead; EMS's `size=200` needs no client fix.

**Consumer contract tests** (one per client, every public call): EMS `UserApiClientImplContractTest`
(provision x2, patch profile, invitation credentials x3, companies paged, company display name x5),
`PartnerApiClientImplContractTest` (topics + partners list, failure and empty cases); PCS
`CompanyServiceClientContractTest` (replaces `CompanyServiceClientTest`), `UserServiceClientContractTest`
(`/users/me`), `EventManagementClientContractTest` (attendance summary, event summary, 404). Moved and
deleted: `UserApiClientImplGetCompanyDisplayNameTest` (all public), the company case of
`UserApiClientImplPagedListsTest`. `UserServiceClientTest` had no public case; it stays for 0b2.
EMS has no usable `@RestClientTest` slice (`EventManagementApplication` declares its own
`@ComponentScan`, so the slice loads `SecurityConfig` and `CacheConfig`); EMS client tests use
`@SpringBootTest(classes = ...)` with the RestTemplate, Jackson 2 and converter auto-configurations.

**Provider tests**: `S2sProviderContractIntegrationTest` in CUMS (6 operations + examples), EMS (2),
PCS (2, `include=company&size=200` exactly as EMS sends it, with PCS's own CUMS call on the harness).

**#1072 fixture**: `ContractServerTest.should_fail_when_limitExceedsSpecMaximum` sends
`GET /api/v1/companies?page=1&limit=1000`; the `AssertionError` names `limit`, the maximum 100 and the
spec file.

**ITs moved onto the harness**: PCS `PartnerControllerIntegrationTest`, `PartnerPerformanceTest`
(CompanyServiceClient), `PartnerAnalyticsControllerIntegrationTest`,
`PartnerMeetingControllerIntegrationTest`, `PartnerMeetingInviteFailureIntegrationTest`
(EventManagementClient); EMS `TopicSessionDataControllerIntegrationTest` (PartnerApiClient). The mocks in
`TopicControllerIntegrationTest`, `AttendeeTopicControllerIntegrationTest` (CompanyServiceClient) and
`PartnerMeetingRsvpControllerIntegrationTest`, `PartnerNoteControllerIntegrationTest`
(EventManagementClient) were never stubbed nor reached; removed. ITs clear the caches in `@BeforeEach`,
because the real clients are `@Cacheable` where the mocks were not.

**Ratchet** (`be-mock-s2s-client`): start 63 (52 files; corrected from 54 after review). End 54 (53 files) = 63 - 10 retired mocks
+ 1 newly visible (`TestUserApiClientConfig`, `mock(UserApiClient.class)`). The new `mock(XClient.class)`
count skips `java.*` imports (CUMS `TestAwsConfig` mocks `java.net.http.HttpClient`, a boundary).
`be-mock-in-process` also fell by 2 (the deleted `CompanyServiceClientTest` mocked `SecurityContext`
and `Authentication`). No RAISED line; baseline updated with `--update`.
Review fix (same day): `@Mock` fields of own S2S client types are counted too (same type rule), which
made 31 more lines visible (`@Mock UserApiClient` in EmailForwardingServiceTest, NotificationServiceTest,
... and `@Mock PartnerMeetingRsvpClient` in InboundEmailRouterTest). New total: 85 in 84 files. The
retirement is unchanged (10 mocks); the rise is visibility only, locked in with `--update`.

**PartnerMeetingRsvpClientImpl** reads `partner-service.base-url` now; `PartnerMeetingRsvpClientImplTest`
proves the configured URL is hit.

**Verification run (rack, one Gradle job at a time, logs in `~/.cache/batbern-test-logs/`):**
shared-kernel test 511 tests, 0 failures; PCS 241/0; CUMS 839/0; EMS 2517/0 (18 skipped; not compared with a pre-change run);
`vitest run scripts/test-ratchet.test.ts` 47/47; `make check-test-ratchet` OK; `make lint-api` OK
(124 findings, at baseline); checkstyle main/test/testFixtures of the touched modules OK;
`npm run generate:api-types` changes only the `size` description in `partner-api.types.ts`.

## Plan Change Log

- 2026-11-08: partners-api `size` gets a documented "no maximum" instead of a `maximum` (PCS has none;
  a `maximum` would change the endpoint). `GeneratedCodeSemantics` added to the harness (three rules,
  see Implementation Notes). `ContractServer.expectInternalWithoutSpec` added for one provider-side
  `/internal` dependency until 0b2.

## Review Triage Log

Quick review, pass 1 (2026-11-08): 9 findings. Verdicts: medium 3, low 4, false 1, rejected 1. Routes: patch 7, reject 2. No intent_gap or bad_plan, so no loopback.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Workflow and deploy docs in the diff | false | reject | Separate commit `88c5ed81` (owner-requested post-deploy job), in the diff only because the baseline predates it; not part of this story's commit. |
| 2 | #1072 test passes on URI text (`limit`, `100` in `1000`) | medium | patch | Verified: header line carries the URI. Now asserts the validator key, `(parameter 'limit')` added by `OpenApiContract`, and "maximum value of 1E+2". |
| 3 | Custom-body test matches `name` in header, body misses required fields | low | patch | Verified. Body now complete except `name: 42`; asserts `schema.type` at `/name` and no "required" error. |
| 4 | PCS `deriveEventTimes` from sessions never exercised | medium | patch | Verified: example has no sessions. New `should_deriveEventTimes_fromSessions` (schema-validated body, Zurich times asserted). |
| 5 | Failure-path tests without `verify()` | low | patch | Verified: `null` also results when no call is made. `verify()` added to the three tests. |
| 6 | `withQueryParam` compares encoded, validation decodes | low | patch | Verified at ContractServer:253-260 vs 131-134; now decodes; self-test with a space. |
| 7 | Ratchet start file count 54, actual 52 | low | reject (fix edits the plan) | Corrected in Implementation Notes directly; a fact, not a design change. |
| 8 | `@Mock XClient` fields invisible to the ratchet | medium | patch | Verified (31 lines). Counted with the same type rule; ratchet unit test added; total 85 in 84 files, visibility only. |
| 9 | CUMS provider test resets the shared Cognito mock | low | patch | Verified: `Mockito.reset` wipes TestAwsConfig's default stub on the cached context. Now restores only its own stub and clears invocations. |

Deferred to `deferred-work.md` (implementer's open items): PCS session times only for organizers (17.6); `bio: null` vs generated frontend types (17.0c1).
