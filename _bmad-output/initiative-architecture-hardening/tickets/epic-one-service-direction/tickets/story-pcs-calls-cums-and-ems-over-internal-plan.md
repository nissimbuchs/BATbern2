---
title: 'PCS calls CUMS and EMS over /internal'
type: 'refactor'
ticket: '6b'
created: '2026-11-11'
status: 'done'
baseline_revision: 'e0bbba970de03b5bcb9cac599dbe041f44d3d77c'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PCS still calls 3 public `/api/v1` operations (CUMS `GET /companies/{name}`, CUMS `GET /users/me`, EMS `GET /events/attendance-summary`), each client copies its own JWT header code, and the machine-only attendance summary sits in the public `event-analytics-api` (Spectral G01).

**Approach:** Company read → 6's `GET /internal/companies/{name}`; current user → `custom:username` from the JWT plus existing `GET /internal/users/{username}`; attendance summary → new EMS `GET /internal/events/attendance-summary`, removed from the public spec. Every PCS S2S call, `HttpOutboundMailRecorder` included, uses `S2sHeaders.forCurrentUser()`.

## Boundaries & Constraints

**Always:** Owner blanket go 2026-11-10. Commit locally on `feature/17-overnight-2026-11-10`, never open a PR. Verify on local dev only, no real outbound mail. One heavy job at a time, Gradle `--max-workers=4`, Vitest `--maxWorkers=4`, logs under `~/.cache/batbern-test-logs`, long commands wrapped in `timeout`; restart dev services only with `timeout 600 make dev-native-restart-service SERVICE=<name>`. Spec first. `/internal` authorizes by operation; the role and ownership checks stay on PCS `PartnerAnalyticsController` and `/partners/me`. C4: spec, generated types, Bruno, Playwright in the same commit series. Test fidelity: `ContractServer`/`ProviderContract`, no own-code mocks in touched tests.

**Never:** Change `GET /companies/{name}` or `GET /users/me` publicly. Touch `PartnerApiClientImpl` (8), `PartnerMeetingRsvpClient`/`InternalRsvpController` (8b), EMS `UserApiClientImpl` (6). Widen a public endpoint. Change the partner attendance dashboard, its XLSX export or partner contact self-service as the user sees them.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Dashboard, user | PARTNER/ORGANIZER opens analytics (JWT) | PCS → `GET /internal/events/attendance-summary?companyName&fromYear` with Bearer; same rows as today | No error expected |
| No user | Call without Authentication (test, async) | Same calls, no `Authorization`, succeed | No error expected |
| `/partners/me` | PARTNER JWT with `custom:username` | `GET /internal/users/{username}` → `companyId` | No company → `PartnerNotFoundException` as today |
| Unknown user/company | 404 from CUMS | `UserNotFoundException` / `CompanyNotFoundException` as today | As today |
| Old public path | `GET /api/v1/events/attendance-summary` | Not served (404 expected; local dev records the actual code, a validated `{eventCode}` may give 400) | n/a |

**Decisions (2026-11-11, agent under owner's blanket go):** `getCurrentUserProfile` is deleted from `UserServiceClient`; `PartnerContactService` resolves the username with `SecurityContextHelper.getCurrentUsername()` and calls cached `getUserByUsername`, as `PartnerSecurityService` already does (15-min cache lag on a company change, same as the ownership check). The EMS internal response uses a hand-written record and an `AttendanceSummary` schema in `ems-internal`, like `EventSchedule`; `AttendanceSummaryDTO` leaves `event-analytics-api`.

**Decisions (2026-11-11, decided by agent under owner's blanket go):**
- `GET /partners/me` resolves the caller from the `custom:username` claim and reads `/internal/users/{username}`; a token without that claim gets "not linked", as the PCS ownership check (`PartnerSecurityService`) already does. The pre-token Lambda sets the claim on every production token; no internal lookup by Cognito ID is added.
- The plan is ~2100 tokens, above the target; kept whole (one cohesive goal).

</frozen-after-approval>

## Code Map

- `services/partner-coordination-service/.../client/impl/CompanyServiceClientImpl.java` -- L67 `/api/v1/companies/{name}`; own `createHeadersWithJwtToken` L162 (also used by `findCompanyNames`).
- `.../client/impl/UserServiceClientImpl.java` -- `getCurrentUserProfile` L122 (`/api/v1/users/me`); internal reads send `new HttpHeaders()`; own header method L253.
- `.../client/impl/EventManagementClientImpl.java` -- L64 `/api/v1/events/attendance-summary`; `getEventSummary` (internal schedule); header method L161.
- `.../mail/HttpOutboundMailRecorder.java` -- `postForEntity` without headers (async thread).
- `.../service/PartnerContactService.java` L44 -- only caller of `getCurrentUserProfile` (via `PartnerController.getMyPartnerCompany`).
- EMS `controller/EventController.java` L2416-2460 `getAttendanceSummary` (`@PreAuthorize` PARTNER/ORGANIZER, generated `analytics` interface); `config/SecurityConfig.java` L84 matcher; nearest internal: `InternalEventScheduleController`.
- Specs: `docs/api/event-analytics-api.openapi.yml` L30-90 + schema L757; `docs/api/internal/ems-internal.openapi.yml` (add op, consumer PCS); `cums-internal` `/internal/companies/{name}` and `/internal/users/{username}` (add PCS as consumer).
- Tests: PCS `client/{Company,User,EventManagement}ServiceClientContractTest`, `PartnerAnalyticsControllerIntegrationTest` (binds analytics + `ignoringMissingAuthorization`), `PartnerControllerIntegrationTest` (companies-api binding + `@MockitoBean UserServiceClient`), `PartnerContactControllerIntegrationTest` (mocks `UserServiceClient`, `SecurityContextHelper`), `PartnerPerformanceTest`, `config/CumsInternalUsers`. EMS `contract/S2sProviderContractIntegrationTest` L124-130, `controller/EventAttendanceSummaryIntegrationTest`, `securitychain/EmsProductionSecurityFilterChainTest` L147. CUMS `S2sProviderContractIntegrationTest` (public `/users/me`, `/companies/{name}` blocks for PCS). Gateway `config/ProductionSecurityFilterChainTest` L164.
- Gates: `web-frontend/scripts/api-lint-baseline.txt` L8, `api-lint-owners.txt` L20 (G01); `s2s-client-urls-baseline.txt` (3 PCS lines), `s2s-client-urls-owners.txt` S1; `test-ratchet-baseline.txt`.
- Frontend: only `src/types/generated/event-analytics-api.types.ts` changes; dashboard and Playwright `e2e/partner/analytics-dashboard.spec.ts` call PCS. No Bruno, Watch app or frontend caller of the EMS path.
- Docs: `module-map.md` Gates L48, ADR-016 Enforcement, `coding-standards.md` L475 (`ignoringMissingAuthorization` sentence).

## Tasks & Acceptance

**Execution:**
- [ ] `docs/api/internal/ems-internal.openapi.yml` -- `GET /internal/events/attendance-summary` (`companyName` required, `fromYear` optional), `AttendanceSummary` schema with the existing example, JWT optional, consumer PCS. `cums-internal`: name PCS as consumer of the two reads.
- [ ] `docs/api/event-analytics-api.openapi.yml` -- remove the path, `AttendanceSummaryDTO` and the tag text; `npm run lint:api -- --update`, drop G01 from `api-lint-owners.txt`; `npm run generate:api-types`.
- [ ] EMS `InternalAttendanceSummaryController` (new, `controller`) -- same query and default `fromYear`, no role; delete `EventController.getAttendanceSummary` + mapper and the `SecurityConfig` matcher.
- [ ] EMS tests -- `EventAttendanceSummaryIntegrationTest` on the internal path with `jwt()` and anonymously; provider contract test against `ems-internal`; filter-chain test entry removed.
- [ ] PCS clients -- 3 URLs to `/internal`; every call (internal reads too) and `HttpOutboundMailRecorder` use `S2sHeaders.forCurrentUser()`; the 3 private header methods go; delete `getCurrentUserProfile`; javadocs saying "without JWT" corrected.
- [ ] `PartnerContactService` -- username from `SecurityContextHelper`, then `getUserByUsername`.
- [ ] PCS tests -- contract tests bind only internal specs, each moved call with JWT (header present, `expiresAt` set) and without (absent); `PartnerAnalyticsControllerIntegrationTest`, `PartnerControllerIntegrationTest`, `PartnerPerformanceTest` bind internal specs and drop `ignoringMissingAuthorization` where no public binding is left; touch rule: `PartnerControllerIntegrationTest` and `PartnerContactControllerIntegrationTest` move off the `UserServiceClient` mock onto the harness.
- [ ] CUMS `S2sProviderContractIntegrationTest` -- delete the public `/users/me` and `/companies/{name}` PCS blocks; the internal ones cover PCS.
- [ ] Gateway `ProductionSecurityFilterChainTest` -- add `/internal/events/attendance-summary`.
- [ ] Guard -- `npm run check:s2s-client-urls -- --update`, remove owner S1.
- [ ] Docs -- module-map Gates (2 lines left, owner 8), ADR-016 Enforcement, coding-standards L475, doc-drift mappings.

**Acceptance Criteria:**
- Given local dev, when a PARTNER opens the attendance dashboard and exports XLSX, then both show the same data as before and the EMS log shows `/internal/events/attendance-summary`.
- Given local dev, when a PARTNER calls `/partners/me` and the contacts page, then the company resolves and CUMS logs `/internal/users/{username}` and `/internal/companies/{name}`, no `/api/v1/users/me`.
- Given each moved call, when its contract test runs with and without a user JWT, then both pass; the EMS internal IT passes signed in and anonymous.
- Given `lint:api`, then G01 is gone (98 → 97 entries). Given the guard, then the baseline holds 2 lines (5 → 2).
- Given the gateway test, then `/internal/events/attendance-summary` is denied. After deploy (production): `GET /api/v1/events/attendance-summary` does not answer 200 (404 expected, code recorded) and the access-control DAST passes.

## Implementation Notes

- `/partners/me`: a CUMS 404 for the resolved username (a token without `custom:username` falls back to the subject) becomes `PartnerNotFoundException` "not linked" (404, as before for "no company"), per the decision; the client itself still throws `UserNotFoundException`.
- `ResponseConformanceTest` (shared-kernel) used `/events/attendance-summary` as its "most specific template" case; it now uses `/events/registrations` (registrations-api over core). Javadoc and coding-standards follow.
- Also moved: PCS `ProductionChainIntegrationTest` and the PCS provider `S2sProviderContractIntegrationTest` (they bound companies-api for the company read). Touch rule also took `PartnerPerformanceTest` off its `UserServiceClient` mock (`CumsInternalUsers.partnerContacts`). Ratchet: `be-mock-s2s-client` 50 → 47, `be-mock-in-process` 34 → 33. No `ignoringMissingAuthorization` left in PCS.
- Not changed: the gateway's GHSA-qhqv matcher still lists `/api/v1/events/attendance-summary` (anonymous gets 401 there; harmless, out of scope). EMS no longer lists it: with a token the old path reaches `GET /events/{eventCode}` and answers 400 (validated code), measured on local dev.
- Local dev (2026-11-11, as `batbern.partner`): `/partners/me` 200 `sbb`; dashboard 200 (22 rows); XLSX export 200; contacts 200; other company's dashboard 403; PCS log shows `GET :8002/internal/events/attendance-summary` 200, `:8001/internal/users/batbern.partner`, `:8001/internal/companies/sbb`, no `/users/me` or `/api/v1/companies`; CUMS log shows the forwarded JWT on `/internal/users/batbern.partner`. CUMS log for the company read (`GET /api/v1/partners/sbb?include=company` as the partner, 200): `2026-11-11T04:37:12.705+01 Securing GET /internal/companies/sbb` / `04:37:12.784+01 Secured GET /internal/companies/sbb` (first seen 04:30:26). The EMS local log does not log request paths at its level, so the EMS-side line is the PCS client log. Old public path: anonymous 401 (gateway), partner 400; gateway `/internal/events/attendance-summary` 403. Not compared row-for-row against pre-change output (same repository query).

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens + orchestrator), 2026-11-11: 0 high, 3 medium, 5 low, 0 false.

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | ADR-016 claims every EMS client uses `S2sHeaders`; `PartnerApiClientImpl`, `PartnerMeetingRsvpClientImpl` do not | medium | patch | Name PCS + EMS `UserApiClientImpl`, list the EMS exceptions with owners 8 and 8b. |
| 2 | coding-standards says one `ignoringMissingAuthorization` binding left; `CumsContractHarnessTest` is a second | low | patch | Correct the count. |
| 3 | AC1: EMS-side evidence and "same data as before" inferred | medium | patch | Data assertion on the EMS internal endpoint in the with-JWT case (see 6) is the EMS-side evidence; the query is unchanged. |
| 4 | AC2: CUMS log shows `/internal/users/…` but `/internal/companies/{name}` only via PCS log | low | patch | Re-check on local dev, record the CUMS log line. |
| 5 | doc-drift mapping misses CUMS `client/`, PCS `HttpOutboundMailRecorder`, gateway `GatewayUserStatusClient` | low | patch | Audit the class: map every S2S client. |
| 6 | EMS provider test: with-JWT case never checks the data | medium | patch | Assert the data on the same response for both cases. |
| 7 | `CumsInternalUsers.partnerContacts` `manyTimes()` expectation shadows a second company | low | patch | Match by the company parameter, or document the single-use limit. |
| 8 | Gateway still lists the removed `/api/v1/events/attendance-summary` as authenticated-only (orchestrator) | low | patch | Dead matcher (C15). |

## Design Notes

**Architecture note.** Modules: PCS L0 clients and `PartnerContactService`, EMS controllers, specs, web-frontend gate files. No new dependency or cross-service edge; PCS → EMS and PCS → CUMS keep their (upstream) direction. New endpoint: `GET /internal/events/attendance-summary` (nearest `/internal/events/{eventCode}/schedule`; the public one cannot be widened, ADR-016 decision 2). Reused: `S2sHeaders`, `ContractServer`, `ProviderContract`, `findAttendanceSummary`, 6's `/internal/companies/{name}`. Deviation: none.

**Epic 17 rule (C15).** Fixed: 3 public S2S URLs, 3 copied header methods, a dead client method, two own-code mocks. Sizes at start (measured at `e0bbba97`): Spectral baseline 98 entries (105 lines) → 97; guard 5 lines → 2; ArchUnit stores CUMS 4, EMS 6 (2 + 4), PCS has none, unchanged (owned by 7, 7b, 8, 8b); test ratchet `be-mock-s2s-client` 50 (PCS 6), `be-mock-in-process` 34 (PCS 1), expected to fall by at least 2 and 1.

## Verification

**Commands:**
- `set -o pipefail; timeout 3000 ./gradlew <task> --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/6b-<name>.log`, one at a time: `:services:partner-coordination-service:test`, `:services:event-management-service:test`, `:services:company-user-management-service:test`, `:shared-kernel:test`, `:api-gateway:test` -- expected: BUILD SUCCESSFUL, ArchUnit stores unchanged
- `cd web-frontend && npm run lint:api && npm run lint:api-internal && npm run check:api-types && npm run type-check && npm run check:s2s-client-urls` -- expected: exit 0, 97 entries, 2 guard lines
- `make check-test-ratchet && make check-frontend-api-paths` -- expected: exit 0, ratchet only falls

**Manual checks:**
- Local dev: restart `partner-coordination`, `event-management`, `company-user-management`. As PARTNER: dashboard, XLSX export, `/partners/me`, contacts page work; `/tmp/batbern-1-partner-coordination.log` and the EMS/CUMS logs show the `/internal` paths only. `GET /api/v1/events/attendance-summary` through the gateway: record the code.

Final verification 2026-11-11 after the review patches (05f35f11): shared-kernel, CUMS, EMS, PCS and gateway suites green; lint:api (97), lint:api-internal, check:api-types, type-check, check:s2s-client-urls (2), test ratchet, API paths green; Vitest 5905 passed and 1 failed: the MSW examples floor (245 JSON 2xx responses after attendance-summary left the public spec), adjusted to > 244 and re-run green. Local dev: partner dashboard, export, contacts, /partners/me through /internal (PCS and CUMS logs). After deploy: old path not 200, access-control DAST.
