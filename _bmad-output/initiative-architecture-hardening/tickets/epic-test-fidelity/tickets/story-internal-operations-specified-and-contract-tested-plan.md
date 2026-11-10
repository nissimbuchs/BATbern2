---
title: '/internal operations specified and contract-tested (17.0b2)'
type: 'feature'
ticket: '0b2'
created: '2026-11-08'
status: done
baseline_revision: 'aab7770b25bcd6e1a4679af7299675127c38a91c'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-architecture-hardening/tickets/epic-test-fidelity/tickets/story-s2s-contract-harness-on-the-public-api-clients-plan.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The 13 service-to-service calls to `/internal/**` have no OpenAPI spec, so the 0b1 harness cannot check them, and their tests mock the clients (38 `@MockitoBean UserApiClient` in EMS, 10 `UserServiceClient` in PCS, `TestUserApiClientConfig` in 10 EMS tests). Investigation found defects of the kind this hides: PCS user lists stop at 100 without paging, and usernames go into paths unencoded.

**Approach:** One spec per provider in `docs/api/internal/` (outside every public-API check), the harness routing those specs, a contract test for every `/internal` client call and a provider test per provider; fix the defects found; move the cheapest groups of user-client mocks onto the harness.

## Boundaries & Constraints

**Always:** specs describe the endpoints as they are today (17.6 redesigns and extends them); schemas reuse the public spec's components by `$ref` where the DTO is generated from it; a request to an `/internal` path or parameter the spec lacks fails; the frontend path check, API similarity, ZAP coverage and access-control probe keep ignoring `docs/api/internal/` (the gateway denies `/internal/**`), and a test proves it for the frontend path check; ratchet ends lower than it starts.

**Never:** no endpoint behaviour change except the defect fixes named below; no new security model for `/internal` (17.6); `expectInternalWithoutSpec` does not survive: every `/internal` call is specified.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| specified call | EMS `getUserByUsername("anna.meier")` | request validated against `cums-internal`, example returned | none |
| unspecified path | a client calls `/internal/users/{u}/roles` | test fails | message names method, path, spec |
| PCS list > 100 | CUMS answers page 1 with `hasNext: true` | PCS requests page 2 and returns all users | none |
| username needing encoding | username `jean-luc.o'brien` or with a space | path segment percent-encoded, provider resolves it | none |
| merge blocked | CUMS calls EMS merge, EMS answers 409 `MERGE_BLOCKED` | `MergeBlockedException`, as today | response validated against the spec's 409 |

**Decision (owner, 2026-11-08):** `docs/api/internal/` is linted with a reduced Spectral ruleset of its own (structure, examples, the personal-data-in-path rule), without the naming rules (`no-verb-segment` and the like); the internal paths keep their verbs until 17.6 redesigns them. The public `lint:api` run and its baseline stay unchanged.

</frozen-after-approval>

## Code Map

- `shared-kernel/src/testFixtures/.../contract/OpenApiContract.java:48` `BASE_PATH = "/api/v1"` hardcoded, `route()` ~122 drops other paths -- take the base path from the spec's `servers` (internal specs: `/internal`, or `/` with full paths). `ContractServer.java:103` `expectInternalWithoutSpec` -- remove after its one user (PCS `contract/S2sProviderContractIntegrationTest.java:79`) has a spec.
- Providers (all `permitAll` on `/internal/**`, gateway `denyAll`, `api-gateway/.../SecurityConfig.java:206`):
  - CUMS `controller/InternalUserController.java`: `GET /internal/users/{username}?include` (44), `GET /internal/email-owners?email` (59), `GET /internal/users?role&company&page&limit` (65), `POST /internal/users/get-or-create` (83, 409 `EMAIL_LINKED_TO_ACCOUNT`). DTOs generated from `users-api.openapi.yml` (`UserResponse` ~2818, `PaginatedUserResponse`, `GetOrCreateUser*`).
  - EMS `usermerge/InternalUserMergeController.java` (merge-preview 31, merge 36, primary-email 47; 409 `MERGE_BLOCKED`, 400 `INVALID_MERGE`), `mailcenter/InternalMailController.java:116` (`POST /internal/mails` → 202), `mailcenter/InternalRequestConversationController.java` (189-209: merge/logo requests and `/{requestId}/done`). Hand-written records.
  - PCS `controller/InternalRsvpController.java:36` (`POST /internal/partner-meetings/rsvps`, `RecordRsvpRequest` → `RsvpDTO`, 400 on unknown `partStat`).
- Clients: EMS `UserApiClientImpl` (103, 201, 273, 362, 407, 729, 849), `PartnerMeetingRsvpClientImpl:43`; PCS `UserServiceClientImpl` (59, 160, 181), `mail/HttpOutboundMailRecorder:57`; CUMS `client/HttpEventManagementMailCenterClient` (43-58), `HttpEventManagementUserMergeClient` (50, 65, 76); gateway `client/GatewayUserStatusClient:62`.
- Defects: unencoded usernames in paths (EMS 103/273/362/407, PCS 59); PCS lists (160, 181) send no `page`, ignore `hasNext` (EMS pages, 729); gateway Javadoc l.25-26 says the JWT is forwarded, code sends none (l.66); CUMS merge-preview URI built without `encode()`.
- Old `/internal` client tests to replace: EMS `UserApiClientImplInternalApiTest`, `UserApiClientImplGetOrCreateUserTest`, the internal cases of `UserApiClientImplPagedListsTest`; PCS `UserServiceClientTest`; gateway `GatewayUserStatusClientTest` (`@Mock RestTemplate`).
- Tooling read `docs/api` flat (`api-lint.ts:77-82`, `specMatcher.ts:62-66`, `api-similarity.ts:204`, `check-zap-spec-coverage.py:17`, `access-control-check.py:50`): a subfolder is ignored today; only `.github/workflows/api-contract-validation.yml:9-19` triggers on `docs/api/**`.
- Mocks, cheapest first (EMS test dir `src/test/java/ch/batbern/events/`):
  - `config/TestUserApiClientConfig.java` (stubs `getUserByUsername`, `validateUserExists`; 10 importers) -- replace by a config providing the real `UserApiClientImpl` on a harness that answers `GET /internal/users/{u}` from the spec, then delete it.
  - EMS `@MockitoBean UserApiClient` with no stubs (10: CalendarDeclineService, DeregistrationController, DeregistrationLinkOnce, EmailCaseInsensitiveMatching, LiveTimingService, NewsletterSendIteration, NotificationController, SlotAssignmentController, TopicSessionDataController, TopicSimilarityController) and stubbing only `getUserByUsername` (7: AttendeeDashboard, ConversationReply, NewsletterController, PublicSessionVisibility, SessionBatchImport, SpeakerPortalAuth, StructuralSessionController) -- onto the shared harness config, or removed when the client is never called.
  - PCS `@MockitoBean UserServiceClient` with no stubs (PartnerMeetingRsvpController, PartnerNoteController) or `getUserByUsername` only (AttendeeTopicController, PartnerAnalyticsController, TopicController) -- same.
  - The registration, mail-composer and preferences groups stay under the touch rule.

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/internal/{cums,ems,pcs}-internal.openapi.yml` -- every operation above, with examples, status codes as the controllers return them, `$ref` to the public components; a README line in `docs/api/` saying what the folder is and that public tooling ignores it.
- [x] harness -- base path from the spec; remove `expectInternalWithoutSpec`; self-test routing an internal spec and failing an unspecified internal path.
- [x] contract tests -- one per client class for every `/internal` call (EMS, PCS, CUMS, gateway), replacing the old `/internal` client tests; provider tests per provider for its internal operations (extend the 0b1 `S2sProviderContractIntegrationTest`s).
- [x] defects -- encode usernames (`UriComponentsBuilder`/`buildAndExpand`), PCS paging like EMS, CUMS preview `encode()`, gateway Javadoc; each with a test from the matrix.
- [x] mocks -- shared harness test config per service; move the groups above; delete `TestUserApiClientConfig`; `make check-test-ratchet`, `--update`.
- [x] `web-frontend/scripts/check-api-paths.test.ts` (or `specMatcher` test) -- a spec in `docs/api/internal/` is not loaded.
- [x] `docs/api/internal/.spectral.yaml` + a `lint:api-internal` script beside `lint:api` (wired into the same CI job) -- reduced ruleset per the decision; the internal specs pass it with no baseline.
- [x] `docs/architecture/coding-standards.md` harness paragraph and `module-map.md` services section -- where the internal specs live; record ratchet start/end in Implementation Notes.

**Acceptance Criteria:**
- Given every client call to `/internal/**`, when the contract tests run, then each has a passing test against `docs/api/internal/`, and a call to a path the spec lacks fails.
- Given the branch, when `npm run check:api-paths` and `npm run lint:api` run, then they pass unchanged by the new folder.
- Given the branch, when `make check-test-ratchet` runs, then `be-mock-s2s-client` is lower than at the start (85) with no RAISED line.

## Design Notes

Architecture note: modules: shared-kernel test fixtures, EMS/PCS/CUMS/gateway clients and tests, `docs/api/internal/` (new) · new dependencies: none · endpoints: none new; existing `/internal` ones specified as they are · reused: 0b1 harness, public `users-api` and `partner-meetings-api` components · deviations: none (module-map "Calls go downstream→upstream, to `/internal/**` only" unchanged).

## Verification

Rack rules: one Gradle job at a time, `--max-workers=4`, logs via `tee` to `~/.cache/batbern-test-logs/`, memory check before each run (stop above 12 GB). Never `./gradlew --stop` (other sessions share the daemons).

**Commands:**
- `./gradlew :shared-kernel:test :api-gateway:test --max-workers=4` -- expected: pass.
- `./gradlew :services:partner-coordination-service:test`, then CUMS, then EMS, each `--max-workers=4` -- expected: pass.
- `cd web-frontend && npm run check:api-paths && npm run lint:api && npx vitest run scripts/` -- expected: pass.
- `make check-test-ratchet` -- expected: OK, no RAISED.

## Implementation Notes

Implemented 2026-11-08 on `feature/17-0b-s2s-contract-harness`, uncommitted (no commit asked for).

**Specs** (`docs/api/internal/`): `cums-internal` (4 operations), `ems-internal` (8), `pcs-internal` (1):
server `/`, full paths, so the path in a spec is the path on the wire. Schemas `$ref` the public
components where the DTO is generated from them (`UserResponse`, `PaginatedUserResponse`,
`GetOrCreateUser*`, `RsvpDTO`, `ErrorResponse`); the hand-written EMS records and the PCS
`RecordRsvpRequest` have own schemas. `docs/api/README.md` (new) says what the folder is.

**Harness** (shared-kernel test fixtures): base path from the spec's first server URL
(`/api/v1`, or empty for `/`); `BASE_PATH` constant and `expectInternalWithoutSpec` removed.
Additions, each with a self-test: one `ContractServer` can bind several specs (each request goes to
the spec that has its path; a path no spec has fails naming method, path and all specs);
`respondPerRequest` (body and status per call from path variables, query, body; checked against the
spec); `respondWithExample(HttpStatus)` for declared error examples; `onlyFor(Predicate<URI>)` (routes
only matching requests to the harness, others to the real factory); path segments decoded one by one
with `/` and `%` kept encoded, so an encoded username stays one parameter; a validator crash becomes
an `AssertionError` (a client's `catch (Exception)` swallowed it once during the build);
`ProviderContract.assertExamplesMatchSchema` no longer demands an example for a bodiless 202/204.
`ContractServerTest` also checks every response example of every internal spec.

**Contract tests, one per client class, every `/internal` call:** EMS
`UserApiClientImplInternalContractTest` (19, replaces `UserApiClientImplInternalApiTest`,
`...GetOrCreateUserTest`, `...PagedListsTest`), `PartnerMeetingRsvpClientImplContractTest` (renamed
from `...Test`); PCS `UserServiceClientContractTest` (now also the internal calls; replaces
`UserServiceClientTest`), `HttpOutboundMailRecorderContractTest` (renamed, now validated); CUMS
`HttpEventManagementUserMergeClientContractTest`, `HttpEventManagementMailCenterClientContractTest`
(new: these clients had no test); gateway `GatewayUserStatusClientContractTest` (replaces the
`@Mock RestTemplate` test). Provider tests: the three `S2sProviderContractIntegrationTest`s run every
internal operation through the real controller (CUMS incl. 404 and an encoded username; EMS incl.
409 `MERGE_BLOCKED` with two primary speakers at one event, 400s, 202, 204; PCS 200/400/404).

**Defects fixed, each test seen red on the old code first** (old client files swapped in, 9 tests
failed, swapped back):
- Usernames concatenated into paths: EMS `UserApiClientImpl` (all `/internal/users/{u}` reads and the
  three public paths with a username or company slug), PCS `getUserByUsername`, gateway (encoded
  now via `encode().buildAndExpand(...).toUri()`). Latent rather than live: CUMS usernames are
  constrained to `^[a-z]+\.[a-z]+(\.[0-9]+)?$` by a DB check (V4), so no stored username needs
  encoding today; a company slug with `/` would have broken `GET /companies/{name}`.
- Query values encoded leniently (`+` and `&` left as is): EMS `getEmailOwners` sent a plus address
  (`success+...@`) unencoded, which CUMS decodes as a space, so the by-email deregistration lookup
  missed plus addresses; EMS and PCS company filters; CUMS merge-preview (built without `encode()`).
  All strict now (`{var}` templates + `encode()` before `buildAndExpand`).
- PCS `getUsersByRole` / `getUsersByCompanyAndRole` read only page 1 (100 users, `hasNext` ignored):
  now page by page like EMS, at most 100 pages.
- Gateway Javadoc said the JWT is forwarded; corrected (no JWT, internal API).

**Found and left as is (spec documents it, Story 17.6):** PCS answers an unknown `partStat` with 400
and no body, every other 400 with `ErrorResponse`; the spec declares the `ErrorResponse` and says so
in the description; the provider test checks that case by status only.

**Mocks.** `TestUserApiClientConfig` was not used by 10 importers only: EMS's own
`@ComponentScan("ch.batbern.events")` picks every `@TestConfiguration` in the test tree up, so its
`@Primary` Mockito `UserApiClient` was in every EMS integration test context. Its replacement
`CumsContractHarnessConfig` is scanned the same way: `CumsContractHarness` binds the shared
`RestTemplate` once per context, only for requests to `user-service.base-url`
(`onlyFor`; PCS calls go to the real factory as before), validates against cums-internal, users-api
and companies-api, and answers the user, user-list and email-owner reads with the old mock's data,
company detail with 404 (the client then returns `null`, as the mock did). Everything else (get-or-
create, provision, profile patch, invitation credentials, company list) fails unless the test
registers an answer. Full EMS suite with it: first run 29 failures, all fixed in the tests:
5 tests that stubbed the shared mock (`DomainEventListeners`, `VenueCoordination`,
`SpeakerAutoRegistration`, `SpeakerWorkflowService`, `ContentSubmission`) now register harness
answers and read `cums.calls(...)`; test usernames CUMS could never hold changed (`dora` to
`dora.meier`, random `speaker.<hex>` to letters); `.example` addresses (rejected by the email format)
to `.example.com`. Moved off `@MockitoBean`: EMS `UserApiClient` 17 (10 without stubs, 7 with
`getUserByUsername` stubs), PCS `UserServiceClient` 5 (`CumsInternalUsers` registers the lookup on the
test's `ContractServer`). The registration, mail-composer and preferences groups are untouched.

**Ratchet** (`make check-test-ratchet`): `be-mock-s2s-client` start 85 (84 files), end 62 (61 files):
-22 `@MockitoBean`, -1 `TestUserApiClientConfig`. `be-mock-in-process` 57 to 55 (the deleted
`UserServiceClientTest` mocked `SecurityContext` and `Authentication`). No RAISED line; baseline
updated with `--update`.

**Tooling.** `docs/api/internal/.spectral.yaml` (reduced ruleset per the owner decision:
`spectral:oas` off, then structure rules, `oas3-valid-media-example`, `oas3-valid-schema-example`,
`oas3-unused-component`, and `no-personal-data-path-param` from the public functions); checked with
a probe spec that it reports an email path parameter and a wrong example, and that it does not
report a verb segment. `npm run lint:api-internal` (no baseline), wired into `build.yml` right after
`lint:api` and into `make lint-api`. The public tools read `docs/api` flat and need no change;
`check-api-paths.test.ts` proves the frontend path check does not load the folder and fails a
frontend call to `/internal/users/...`. Pre-push hook maps each internal spec to its provider and
consumers.

**Verification (rack, one Gradle job at a time, logs in `~/.cache/batbern-test-logs/0b2-*`):**
shared-kernel 523/0 (30 of them `ContractServerTest`); api-gateway 328/0; PCS 245/0
(1 skipped); CUMS 861/0 (25 skipped); EMS 2539/0 (18 skipped, as before); checkstyle main/test of
all touched modules and testFixtures OK; `npx vitest run scripts/` 114/114; `npm run check:api-paths`
301/301; `npm run lint:api` 124 findings at baseline; `npm run lint:api-internal` clean;
`make check-test-ratchet` OK.

## Plan Change Log

- 2026-11-08: `TestUserApiClientConfig` turned out to be global (component scan), so its replacement
  is global too and answers mock-equivalent defaults; five tests that stubbed the shared mock moved
  onto the harness (not listed in the plan). Harness grew multi-spec binding, `respondPerRequest`,
  `onlyFor` and segment-wise decoding. Defect list extended by the lenient query encoding
  (plus addresses in `getEmailOwners`, company filters). PCS 400-without-body documented, not fixed
  (behaviour change, 17.6).
- 2026-11-08 (review round): PCS `CompanyServiceClientImpl` path and `EventManagementClientImpl`
  `companyName` query strictly encoded (with contract cases); CUMS harness user list keyed on role and
  company (`CumsContractHarnessTest`); per-test binding displacing the CUMS harness documented, with
  `onlyFor` fall-through proven; gateway contract test on the real `cumsStatusRestTemplate` bean;
  `onlyFor` self-test without network; merge-preview 400 declared `anyOf [MergeError, ErrorResponse]`
  (also `/merge`: `oneOf` would reject every MergeError, which is a valid ErrorResponse too);
  module-map names the CUMS→EMS exceptions (17.7); pre-push maps internal specs to shared-kernel and
  users-api to its consumers; real personal addresses in five sources replaced by `@example.com`.

## Review Triage Log

Quick review, pass 1 (2026-11-08): 10 findings, all verified real. Verdicts: medium 4, low 6. Routes: patch 10. No intent_gap or bad_plan, so no loopback.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | PCS `CompanyServiceClientImpl:59` path and `EventManagementClientImpl:61-65` query still unencoded (same class as the EMS fix) | medium | patch | Verified by reading both lines. Strict encoding plus a contract case each (`Meier/Partner AG`, `Meier+Partner & Co`). |
| 2 | `module-map.md` names the merge as the only upstream→downstream call; four CUMS→EMS calls exist and are now specified | medium | patch | Verified (line 15). Named as existing exceptions owned by 17.7, "Do not add another" kept; `06-backend-architecture.md` aligned. |
| 3 | `CumsContractHarness` ignores the `company` filter | medium | patch | Verified: answer keyed on `role` only. Keyed on role + company; `CumsContractHarnessTest` proves a wrong company gets no users. |
| 4 | Pre-push misses shared-kernel for internal specs and the consumers of users-api | low | patch | Verified in `.githooks/pre-push`. Mapping extended. |
| 5 | A per-test `ContractServer.bindTo` displaces the global CUMS harness | low | patch | Verified (shared RestTemplate). Documented in Javadoc and coding-standards with the `onlyFor` fall-through; tested. |
| 6 | Gateway contract test builds its own RestTemplate | low | patch | Verified. Now `@SpringBootTest` on `WebClientConfig` with the real `cumsStatusRestTemplate`. |
| 7 | Harness self-test calls `localhost:9` over the network | low | patch | Verified. Routed to a stub request factory. |
| 8 | Owner's real address in test sources (CLAUDE.md: `@example.com` only) | medium | patch | Verified; class audit found 5 sites (2 EMS tests, 2 frontend tests, CUMS Javadoc). All synthetic now. |
| 9 | PCS `PAGE_SIZE` Javadoc claims an internal maximum | low | patch | Verified: `cums-internal` declares none, controller enforces none. Comment corrected. |
| 10 | merge-preview 400 omits `ErrorResponse` | low | patch | Verified. Implementer used `anyOf`, not the requested `oneOf`: `MergeError` also satisfies `ErrorResponse`, so `oneOf` rejects every real 400; `/merge` corrected the same way. Accepted. |
