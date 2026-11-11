---
title: 'API conventions III b: additional-email key and get-or-create (breaking)'
type: 'refactor'
ticket: '10d'
created: '2026-11-11'
status: 'built'
baseline_revision: 'bade6e7cac19713d14d8df8cddad382c6e954984'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Four CUMS operations put the caller's e-mail address in the path (`/users/me/additional-emails/{email}`: PATCH, DELETE, `make-primary`, `resend-verification`), so the address lands in access logs, browser history and caches (ADR-003 amendment). `POST /companies:get-or-create` uses the retired colon form and answers 200 whether it created or found (ADR-013 §2).

**Approach:** Breaking, no aliases, as in 4.10 to 4.10c. The four operations take `{emailId}`, the existing `user_additional_emails.id` UUID (V16, no migration), loaded scoped to the caller; `AdditionalEmail` gains a required `id`. Get-or-create becomes `POST /companies/get-or-create`, 201 created / 200 found. Spec, generated types, backend, frontend, Bruno, Playwright and docs change together (C4).

## Boundaries & Constraints

**Always:**
- Owner blanket go 2026-11-10. Commit locally on `feature/17-overnight-2026-11-10`; never open a PR. Verify on local dev only. No real outbound mail.
- **Never run make-primary's happy path against local dev or anything that uses the production Cognito pool.** Its happy path runs only in the IT with Cognito at the boundary (`InMemoryCognito`). Bruno and Playwright call make-primary only for an unverified, unknown or foreign id.
- Contract first (`docs/api/`, then `npm run generate:api-types`); spec examples carry `id`; `@PreAuthorize` stays on every write; test fidelity rules; touched tests come off own-code mocks and class-level `@Transactional`.
- Each removed path is pinned by an IT to the exact status it now gets.
- One heavy job at a time on rack: Gradle `--max-workers=4`, Vitest `--maxWorkers=4`, logs via `tee` under `~/.cache/batbern-test-logs/` behind `set -o pipefail`, long commands in `timeout`. Restart dev services only with `timeout 600 make dev-native-restart-service SERVICE=<name>`.

**Never:**
- No aliases or kept old paths. No change to the verify link (`/verify-email?token=`), `POST /users/me/additional-emails`, `/users/additional-emails/verify`, or `/internal/users/get-or-create`.
- No Flyway migration. Do not touch `mailcenter.notify-instead-of-forward`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Change setting | `PATCH /users/me/additional-emails/{emailId}` own row | 200 `AdditionalEmail` with `id` | `receiveEventMail:true` on unverified: 409 `NOT_VERIFIED` |
| Remove | `DELETE …/{emailId}` own row | 204, row gone | — |
| Resend | `POST …/{emailId}/resend-verification` own unverified | 204, one mail in the SES recorder | verified: 409 `ALREADY_VERIFIED` |
| Make primary | `POST …/{emailId}/make-primary` own verified | 204; addresses swapped (IT only, `InMemoryCognito`) | unverified: 409 `NOT_VERIFIED`, Cognito untouched |
| Foreign or unknown id | another user's or a random `emailId`, any of the 4 | 404, row and Cognito unchanged, no mail | — |
| Anonymous | any of the 4 | 401 | — |
| Get-or-create new | `POST /companies/get-or-create` new display name | 201 `CompanyResponse` | blank name: 400; anonymous: 401 |
| Get-or-create existing | same name again | 200, same `name`, no duplicate | — |
| Old paths | the 4 `…/{email}` operations, `POST /companies:get-or-create` | exact status pinned per IT (measured, see Open Questions) | — |

**Decisions (2026-11-11, decided by agent under owner's blanket go):**
- Old `{email}` paths: pin the measured status in the ITs (CUMS answers 400 when the path parameter is not a UUID), and the ticket's verify line says "404 or the pinned status", as 4.10 did.
- `AdditionalEmail.id` reaches EMS in the internal user response; EMS neither stores nor uses it. Accepted, noted in the ADR-003 amendment table.
- Bruno `companies-api` runs against production after every deploy, so it must never create a company there: the production collection checks only get-or-create of an existing company (200). The 201 (created) case is covered by the IT and, if Bruno covers it, only in a dev-only collection with the `BRUNOTESTCO` prefix and cleanup.

</frozen-after-approval>

## Code Map

- **Spec:** `docs/api/users-api.openapi.yml` L2228-2402 (4 ops), `AdditionalEmail` L3470 (add `id`, uuid, required), examples with filled `additionalEmails` (L746-1372); `companies-api.openapi.yml` L747 (also stale text: "anonymous contexts", `testco-2`). `docs/api/internal/cums-internal.openapi.yml` refs `UserResponse`, so EMS sees `id`.
- **CUMS:** `controller/UserController.java` L318-370; `service/UserService.java` `updateAdditionalEmail` L1311, `deleteAdditionalEmail` L1336, `resendVerification` L1206; `service/PrimaryEmailService.makePrimary`; `repository/UserAdditionalEmailRepository` (add `findByIdAndUser`); `UserResponseMapper.mapAdditionalEmailToDto` (set id); `controller/CompanyController.java` L84-95, `CompanyService.getOrCreateCompanyResponse` L159 (needs a created flag; `getOrCreateCompany` stays for `UserService`).
- **CUMS tests:** `UserAdditionalEmailsIntegrationTest` (9 refs), `PrimaryEmailIntegrationTest` (5), `AdditionalEmailVerificationIntegrationTest` (4), `integration/CompanyControllerIntegrationTest` L113-210.
- **Other suites:** shared-kernel `ContractServerTest` L507 (sample body lacks `id`), `LoggingUtilsTest` L309 (string example only); gateway `DomainRouterTest` L617-646 (`%40` encoding regression), `LogSanitizerTest` L19-24; EMS fixtures that build `AdditionalEmail` through `CumsContractHarness` (`GlobalMailCenterIntegrationTest` L379, `RegistrationStatusIntegrationTest` L296, `ParticipantsControllerIntegrationTest`, `UserApiClientImplInternalContractTest`).
- **Frontend:** `services/api/userAccountApi.ts` L368-490 (4 calls take `emailId`), `hooks/useUserAccount/useUserAccount.ts` L182-215, `components/user/ProfileEmailAddresses/ProfileEmailAddresses.tsx` L128-160 and test, `UserSettingsTab/UserSettingsTab.tsx` L186-328 and test; `services/api/companyApi.ts` L186, `CompanyAutocomplete.tsx`/test (comments), `ProfilePage.tsx` L223.
- **Bruno:** `users-api/15`, `16-list-with`, `17-delete` (`encodedAdditionalEmail`), `18`, `19`; `companies-api` has no get-or-create request. Both collections also run after deploy against production.
- **Playwright:** `e2e/organizer/user-settings-additional-emails.spec.ts` (`@gate`; cleanup L32 deletes by address).
- **Lint:** `web-frontend/scripts/api-lint-baseline.txt` L9-12, L43; `api-lint-owners.txt` N8 L21, N9 L20, X3 L36.
- **Checked, no caller:** no S2S client calls `/companies:get-or-create` (EMS uses `/internal/users/get-or-create`, PCS only `/internal/companies`); no gateway or CUMS security matcher names these paths; mail templates link only `/verify-email?token=`.

## Tasks & Acceptance

**Execution:**
- [x] Record the start sizes (Design Notes).
- [x] Additional e-mails: spec first (4 ops on `{emailId}` `format: uuid`, `AdditionalEmail.id` required, every example). RED ITs on the production chain: per op own / foreign / unknown / anonymous, unverified 409s, old `{email}` paths pinned; make-primary happy path with `InMemoryCognito`. Then repository `findByIdAndUser`, service and controller; move touched ITs off class-level `@Transactional`. Fix the shared-kernel sample, EMS fixtures, gateway examples. Commit.
- [x] Get-or-create: spec (slash path, 201 and 200, correct description), RED IT (201 new, 200 existing with no duplicate row, 400, 401, old path pinned), then service result with a created flag and the controller. Commit.
- [x] Frontend: regenerate types; services, hooks and both components call by `a.id` (test ids and React keys may stay on the address); `ProfileEmailAddresses.test` onto MSW (drops 2 `fe-mock-services` entries and the i18n mock); `companyApi.ts` new path. Commit.
- [x] Bruno: `15` stores `additionalEmailId`; `17-delete` uses it; new `16a` PATCH `receiveEventMail:false` 200, `16b` make-primary on the unverified row 409, `16c` unknown id 404 on make-primary and resend, `16d` partner token on the organizer's id 404. `companies-api`: new get-or-create request with a display name whose slug starts `brunotestco` (pretest sweep removes it): 201, repeat 200, then DELETE. Prose in `docs {}`.
- [x] Playwright: cleanup deletes by id from `GET /users/me`; add API checks on the added (unverified) row: make-primary 409, unknown id 404.
- [x] Lint `--update`: N8 and N9 gone, X3 regex extended by the 4 `{emailId}` operations; ratchet `--update` (shrink only). Docs: ADR-013 note for 10d, ADR-003 amendment table (additional e-mails under "natural key is personal data"), coding-standards L879/L884 examples, `06b-user-lifecycle-sync.md` L1056-1094, `user-guide/entity-management/users.md` L745, doc-drift mapped docs. Commit.

**Acceptance Criteria:**
- Given the specs, when `npm run lint:api` runs, then the baseline holds 87 entries, `no-personal-data-path-param` and `path-kebab-case` report 0, the 4 `{emailId}` lines sit under X3, and nothing is UNOWNED or STALE.
- Given CUMS ITs, then each matrix row passes, and after make-primary a reload of `GET /users/me` lists the old primary as a confirmed additional address under a new `id`.
- Given local dev, when a user adds, switches, resends and removes an additional address on the profile page and in the settings tab, then each change persists after a reload and the resend lands in `/dev/mails`.
- Given `make check-frontend-api-paths`, then it passes.
- After deploy (owner): the old paths answer their pinned status in production.

## Implementation Notes

- Start sizes as in Design Notes (measured at `bade6e7c`: baseline 88, ratchet 585). End: baseline 87,
  ratchet 580 (the three additional-email ITs off class-level `@Transactional`, ProfileEmailAddresses.test
  off 2 service mocks and the i18n stub).
- Measured old-path statuses: the 4 `{email}` operations answer 400 (MethodArgumentTypeMismatch, no
  UUID), `POST /companies:get-or-create` 404; pinned in the ITs, the PATCH one also in Bruno
  `users-api/17a`.
- Get-or-create ITs left the class-level `@Transactional` CompanyControllerIntegrationTest for a new
  committing CompanyGetOrCreateIntegrationTest; the rest of that file keeps its baseline entry.
- Bruno 201 case: per the frozen decision, in the new dev-only collection `company-get-or-create-api`
  (registered in `run-bruno-tests.sh`); `companies-api/02a` checks only the 200 of the company 01 created.
- Found, not fixed (pre-existing, test-only shape): resend-verification to a reserved-domain address
  (`*.invalid`) answers 500, because the synchronous send throws `ReservedEmailRecipientException`
  unhandled (the add path swallows it). Real addresses are not affected.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens), 2026-11-11: 0 high, 3 medium, 4 low, 0 false.

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | Touched tests keep own-code `userApiClient` mocks and class-level `@Transactional` (RegistrationStatus, GlobalMailCenter, AttendeeDashboard, CompanyController ITs) | medium | patch | Touch rule; move the touched cases to `CumsContractHarness` / committing tests. |
| 2 | Resend-verification to a reserved-domain address answers 500 (no handler for `ReservedEmailRecipientException` in CUMS) | medium | patch | Pre-existing, found here; owner rule "fix what you find": 422 like EMS, test first, own commit. |
| 3 | `company-get-or-create-api/01` key is always `brunotestco1`, colliding with `companies-api/01` | medium | patch | Unique 12-character key per run. |
| 4 | Copied `docs {}` text wrong in the new collection's 00 and 99 | low | patch | Direct correction. |
| 5 | ADR-003 amendment table row does not carry the EMS acceptance | low | patch | Direct correction. |
| 6 | User guide lists 2 of the 4 `{emailId}` operations | low | patch | Direct correction. |
| 7 | Spec says get-or-create never suffixes, code still calls `ensureUniqueCompanyName` | low | patch | Remove the dead call. |

## Design Notes

**Architecture note.**
- **Modules:** CUMS user account (additional e-mails, primary address) and companies; no gateway routing change.
- **New dependencies:** none.
- **Endpoints:** `…/additional-emails/{emailId}[/make-primary|/resend-verification]` (nearest: `/users/merge-requests/{requestId}/approve`, a UUID owned by the caller); `POST /companies/get-or-create` (nearest `POST /companies`).
- **Reused:** the 4.10b scoped-load idiom (`findByIdAnd{Owner}`, foreign id = 404); `InMemoryCognito`.

Make-primary deletes the chosen row and inserts a new row for the old primary, so ids change: the frontend refetches `GET /users/me` after it (already invalidates). `get-or-create` cannot shadow `/companies/{name}`: names are letters and digits only.

**Epic 17 rule (C15).** Fixed: personal data in four paths; get-or-create's created/found answer; the spec claiming anonymous access and hyphenated slugs; e-mail-keyed lookups in three service methods.

| Store | Start (`bade6e7c`, measured) | Expected end |
|---|---|---|
| Spectral baseline | 88 | 87 |
| Test ratchet | 585 entries; touched files carry 8 | shrinks |
| ArchUnit CUMS | 4 lines | unchanged |
| s2s client guard | 0 | 0 |

## Verification

**Commands:**
- `set -o pipefail; timeout 3600 ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/410d-cums.log`, then `:shared-kernel:test`, `:api-gateway:test`, `:services:event-management-service:test` (shared `AdditionalEmail` schema), one at a time -- expected: BUILD SUCCESSFUL each
- `cd web-frontend && npm run lint:api && npm run check:api-types && npm run type-check && timeout 1800 npx vitest run --maxWorkers=4` -- expected: green, 87 entries
- `make check-test-ratchet && make check-frontend-api-paths && make check-s2s-client-urls` -- expected: exit 0
- `./scripts/ci/run-bruno-tests.sh development --collection users-api`, then `companies-api` -- expected: PASS, no `Skipping invalid file`
- Playwright `TEST_ENV=development`: `organizer/user-settings-additional-emails.spec.ts` -- expected: pass

**Manual checks:**
- After `timeout 600 make dev-native-restart-service SERVICE=company-user-management` (and `api-gateway`): walk the local-dev AC; no make-primary click on a confirmed address.


Final verification 2026-11-11 after the review patches (6b45d887, 59ad1c70, 2ebf4887): shared-kernel, CUMS, EMS, PCS and gateway suites green; lint:api (87), lint:api-internal, check:api-types, type-check, check:s2s-client-urls, Vitest 5878 passed, test ratchet (578) and API paths green. Local dev before the patches: Bruno users-api 41/41, companies-api 12/12, company-get-or-create-api 5/5, Playwright additional-emails green.
