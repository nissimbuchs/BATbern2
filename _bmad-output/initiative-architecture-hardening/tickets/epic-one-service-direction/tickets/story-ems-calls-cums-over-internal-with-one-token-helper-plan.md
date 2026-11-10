---
title: 'EMS calls CUMS over /internal with one token helper'
type: 'refactor'
ticket: '6'
created: '2026-11-10'
status: 'in-progress'
baseline_revision: '23e6150f42634a669f02bcae495d9d0e56e533bc'
route: 'full'
route_source: 'auto'
risk: 'high'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** EMS `UserApiClientImpl` calls 5 public CUMS `/api/v1` operations with the user's JWT, which forces 3 machine-only operations into `users-api` and gives every client its own header code. ADR-016 says S2S calls go to `/internal`, forward the JWT when present and never require it.

**Approach:** One shared-kernel S2S header helper. Provision, profile PATCH and invitation credentials move to CUMS `/internal` and leave `users-api`. The company read goes to a new `GET /internal/companies/{name}`. Dead `getAllCompanies` is deleted. A client-package guard fails on any `/api/v1` URL.

## Boundaries & Constraints

**Always:** Owner blanket go 2026-11-10. Commit locally on `feature/17-overnight-2026-11-10`, never open a PR. Verify on local dev only, no real outbound mail. One heavy job at a time on this host, Gradle `--max-workers=4`, logs under `~/.cache/batbern-test-logs`. `/internal` authorizes by operation, never by role. The role check stays on the EMS public endpoint where the user entered. The specs come first (contract-first). C4: spec, generated types, Bruno and Playwright change in the same commit series. Test fidelity rules: `ContractServer` and `ProviderContract`, no own-code mocks.

**Never:** Touch the CUMS clients, `PartnerApiClientImpl` or `PartnerMeetingRsvpClientImpl`. Touch the PCS clients (6b). Change `GET /companies/{name}` or `GET /users/me` publicly. Widen a public endpoint. Edit an applied migration. Change speaker invitation, provisioning or content submission as the user sees them.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| User present | Organizer invites a speaker (JWT in SecurityContext) | `POST /internal/users/provision`, then `.../issue-invitation-credentials`, with `Authorization: Bearer <jwt>`; CUMS records the user | No error expected |
| No user | Scheduler, SQS or test thread, no Authentication | Same calls with no `Authorization` header, call succeeds, CUMS records `system:<job>` | No error expected |
| Non-JWT principal | `@WithMockUser` / anonymous | No header, no WARN per call | Same as no user |
| Provision non-SPEAKER role | Internal provision with role ≠ SPEAKER | 400 (by operation: the internal operation grants SPEAKER only) | `UserServiceException` in EMS |
| Unknown user / company | PATCH profile or credentials for a missing user; company slug unknown | 404 → `UserNotFoundException`; company → `null` (slug fallback) | As today |
| Old public path | `POST /api/v1/users/provision` etc. through the gateway | No longer served (404; 405 for provision, see Open Questions) | n/a |

**Decisions (2026-11-10, decided by agent under owner's blanket go):**
- Production check: the 3 moved operations count as not exposed when the public API answers 404 or 405 (`POST /api/v1/users/provision` matches `/users/{username}` and gets 405); the actual codes are recorded after the deploy.
- Every S2S call uses the helper, including the internal reads that 17.0b2 sent without a JWT: CUMS's internal answers do not depend on the caller, so caching stays safe. The `noJwt` assertions become with-JWT and without-JWT cases.
- The plan is ~2400 tokens, above the 1600 target; kept whole (one cohesive goal; the owner pre-approved the checkpoints).

</frozen-after-approval>

## Code Map

- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` -- `provisionUserWithRole` L470 (`/api/v1/users/provision`), `patchUserProfile` L546, `issueInvitationCredentials` L614, `getAllCompanies` L803 (dead, contract test only), `getCompanyDisplayName` L893. `createHeadersWithJwtToken` L938 becomes the helper. Internal reads send `new HttpHeaders()` today.
- `.../events/client/UserApiClient.java` -- interface. Delete `getAllCompanies`, and `validateUserExists` too (no production caller; it shares cache key `#username` with `getUserByUsername`).
- Callers stay unchanged: `SpeakerWorkflowService` L426/L617 and `ContentSubmissionService` L473, which resolves the username server-side, so EMS enforces ownership.
- `services/company-user-management-service/.../controller/UserController.java` -- L226-341 implement generated `DomainIntegrationApi` plus `patchUserProfile` with role and ownership checks. They go away.
- `.../controller/InternalUserController.java`, `InternalCompanyController.java` -- target for the 3 writes and `GET /internal/companies/{name}` (`companyService.getCompanyByName`). `SecurityConfig` L81/L130 already `permitAll` `/internal/**`.
- `.../service/UserService.java` -- `provisionUserWithRole` L587 (ADMIN branch L598), `issueInvitationCredentials` L787, `patchUserProfile` L902 (actor via `getAuditUsername` L1139, falls back to "anonymous").
- `docs/api/users-api.openapi.yml` L2924-3170 (3 ops, tag "Domain Integration"). Their schemas stay in components: EMS codegen (`build.gradle` L793, `models: ''`) and the internal spec `$ref` them, the same pattern as `GetOrCreateUserRequest`.
- `docs/api/internal/cums-internal.openapi.yml` -- add 4 operations (header says "without a JWT": change to "JWT optional").
- Tests: EMS `UserApiClientImpl{,Internal}ContractTest`, `config/CumsContractHarness` (`ANSWERABLE` L79), and the speaker workflow, invitation and auto-registration integration tests that answer the old paths. CUMS `S2sProviderContractIntegrationTest` L110-176 and `UserProvisioningAndPatchIntegrationTest`. Gateway `ProductionSecurityFilterChainTest` L164.
- `web-frontend/scripts/api-lint-baseline.txt` L54 and `api-lint-owners.txt` L21 (G02). `src/types/generated/user-api.types.ts`. No frontend or Playwright caller (comments only).
- `bruno-tests/users-api/22..26-*.bru` (6 files call the 3 ops, set no vars).
- Guard pattern: `web-frontend/scripts/test-ratchet.ts` (Node TS, scans Java, baseline, vitest, `__fixtures__`), owners pattern `api-type-copies-owners.txt`, CI job `test-ratchet` in `.github/workflows/build.yml` L666 (no path filter).

## Tasks & Acceptance

**Execution:**
- [ ] `shared-kernel/src/main/java/ch/batbern/shared/security/S2sHeaders.java` + unit test -- Static helper: `Authorization: Bearer` from a `Jwt` principal (`JwtAuthenticationToken`), empty headers otherwise, no WARN. Content-Type is up to the caller.
- [ ] `docs/api/internal/cums-internal.openapi.yml` -- `POST /internal/users/provision`, `PATCH /internal/users/{username}/profile`, `POST /internal/users/{username}/issue-invitation-credentials`, `GET /internal/companies/{name}` with examples and `$ref` to users-api and companies-api schemas. The JWT is optional. One consumer: EMS speaker workflow (6b adds PCS for companies).
- [ ] `docs/api/users-api.openapi.yml` -- remove the 3 paths and the now-empty tag. Then `api-lint-baseline.txt` (`--update`) and remove G02 from `api-lint-owners.txt`.
- [ ] CUMS `InternalUserController`, `InternalCompanyController`, `UserController`, `UserService` -- add the 4 endpoints (`@Valid`, `additionalProperties` rejection kept). Delete the public methods. Provision grants SPEAKER only. Actor = forwarded `custom:username`, else `system:<job>` (Design Notes).
- [ ] CUMS tests -- move `UserProvisioningAndPatchIntegrationTest` to the internal paths. Each operation runs with `jwt()` and anonymously. Drop the role 403 cases; add SPEAKER-only and actor assertions. `S2sProviderContractIntegrationTest`: the 4 internal operations; delete provision, profile, credentials and the paged company list from the public blocks (keep `/users/me` and `/companies/{name}` for PCS).
- [ ] EMS `UserApiClient(Impl)` -- every call uses `S2sHeaders`. The 4 calls go to `/internal`. Delete `getAllCompanies` and `validateUserExists`, and fix the `CompanyBasicDto` javadoc.
- [ ] EMS tests -- contract tests against `cums-internal`: each moved call once with JWT (header forwarded) and once without (header absent). Update `CumsContractHarness.ANSWERABLE` and the integration tests that answer the old paths.
- [ ] `api-gateway/.../ProductionSecurityFilterChainTest.java` -- add `/internal/users/provision` and `/internal/companies/acme` to the denied paths.
- [ ] `web-frontend/scripts/check-s2s-client-urls.ts` + `s2s-client-urls-baseline.txt` + `s2s-client-urls-owners.txt` + `check-s2s-client-urls.test.ts` + `__fixtures__/s2s-client-urls/` -- scan `services/*/src/main/java/**/client/**` for `"/api/v1`. Baseline lines are keyed by file and URL: PCS 3 → 6b, `PartnerApiClientImpl` 2 → 8. RAISED or STALE fails. `npm run check:s2s-client-urls`, `make check-s2s-client-urls`, and steps in the `test-ratchet` CI job.
- [ ] `bruno-tests/users-api/22..26` -- delete the 6 files and update the collection README.
- [ ] `web-frontend` -- `npm run generate:api-types`.
- [ ] Docs -- `module-map.md` Gates (the guard), ADR-016 Enforcement, the `InternalUserController` javadoc, and docs from `.github/doc-drift-mappings.yml`.

**Acceptance Criteria:**
- Given local dev, when an organizer invites a new speaker, then CUMS logs `POST /internal/users/provision` and `.../issue-invitation-credentials` and the credentials mail lands in `/dev/mails`.
- Given a speaker submits content with a bio, when it saves, then the bio is on the CUMS profile through `PATCH /internal/users/{username}/profile`.
- Given the change, when `users-api.openapi.yml` and the gateway routes are read, then the 3 operations are absent from the public spec and from `user-api.types.ts`. The production check (organizer token, expect 404/405) is recorded for after deploy.
- Given `lint:api`, then G02 is gone (baseline 105 → 104 entries).
- Given a fixture client with `"/api/v1/x"`, when the guard runs, then it fails with RAISED. The real tree passes with 5 baseline lines.
- Given each moved call, when an integration test runs it with and without a user JWT, then both pass. `ProductionSecurityFilterChainTest` still denies `/internal/**`.

## Implementation Notes

## Plan Change Log

## Review Triage Log

## Design Notes

**Architecture note.** Modules: shared-kernel security, EMS L0 clients, CUMS controllers and `UserService`, API specs, web-frontend scripts. New dependency: EMS and later PCS on `S2sHeaders`; no new cross-service edge. New endpoints: 3 writes moved from `users-api` into `InternalUserController` (nearest: `/internal/users/get-or-create`; the public ones cannot stay, ADR-016 decision 2), and `GET /internal/companies/{name}` (nearest: `GET /internal/companies`, which returns only slugs; public `GET /companies/{name}` may not be widened). Reused: `ContractServer`, `ProviderContract`, `CumsContractHarness`, `companyService.getCompanyByName`, the test-ratchet script pattern. Deviation: the internal paths keep their verbs (`provision`, `issue-invitation-credentials`), as `get-or-create` and `merge-preview` do. The internal ruleset has no naming rules (owner, 2026-11-08).

**Guard: source scan, not ArchUnit.** ArchUnit cannot see string literals, and PCS has no ArchUnit gate. `test-ratchet.ts` already scans Java from one CI job with a shrink-only baseline, and the owners file follows `api-type-copies-owners.txt`.

**Actor.** These 3 operations have exactly one consumer, so CUMS records the forwarded user, else `system:ems-speaker-workflow` (named per operation in the spec). No extra header.

**Epic 17 rule (C15).** Violations this story fixes: per-client header copies (EMS now, PCS in 6b), dead `getAllCompanies` and `validateUserExists` (the latter also removes a `userApiCache` key collision), role checks on machine operations, and an internal spec that claimed "no JWT". Sizes at start (measured at `23e6150f`): Spectral baseline 105 entries (112 lines), at the end 104. CUMS ArchUnit store 4 violation lines, EMS 6 (mail core 2, `*Partner*Client*` 4). Both stay unchanged because their lines belong to 7, 7b, 8 and 8b. The guard baseline starts at 5 lines.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew <task> --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/6-<name>.log`, one at a time, for `:shared-kernel:test`, `:services:company-user-management-service:test`, `:services:event-management-service:test`, and `:api-gateway:test --tests ProductionSecurityFilterChainTest` -- expected: BUILD SUCCESSFUL, ArchUnit stores unchanged
- `cd web-frontend && npm run lint:api && npm run lint:api-internal && npm run check:api-types && npm run type-check` -- expected: all exit 0
- `cd web-frontend && npx vitest run scripts/check-s2s-client-urls.test.ts && npm run check:s2s-client-urls` -- expected: pass
- `make check-test-ratchet && make check-frontend-api-paths` -- expected: exit 0 (the ratchet may only fall)

**Manual checks:**
- Local dev: `make dev-native-restart-service SERVICE=company-user-management` and `SERVICE=event-management`. As organizer, invite a new speaker and move them to INVITED. `/dev/mails` holds the invitation with credentials, and `/tmp/batbern-1-company-user-management.log` shows the `/internal` paths. As that speaker, submit content with a bio; `GET /api/v1/users/{username}` shows the bio.
