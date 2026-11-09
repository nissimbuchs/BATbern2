---
title: '4.9 owner additions: idn-email, no fields projection, always-set properties required'
type: 'refactor'
ticket: ''
created: '2026-11-09'
status: 'ready-for-dev'
baseline_revision: ''
route: 'full'
route_source: 'auto'
risk: 'high'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-architecture-hardening/null-vs-optional-options.md'
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The specs misdescribe what the services send.
- CUMS stores IDN addresses (`juerg.brunner@bücherstube.example`), while the specs say `format: email`, which is ASCII only.
- `GET /companies` and `GET /users` offer a `fields` projection that no caller uses. On users it does nothing; on companies it breaks the schema's `required`.
- Many response properties every code path sets are typed optional, so the frontend guards against values that never go missing.

**Approach:** These are the owner decisions of 2026-11-09 (deferred-work.md, last entry).
- Switch `format: email` to `idn-email` where an address can be IDN.
- Remove `fields` with its projection code, and add one ADR-013 §3 line saying a list offers `fields` only when a caller needs it.
- Mark `required` the response properties that every producing path sets, spec by spec, with the response conformance gate as the safety net. Then remove the frontend guards that existed only because of the optional type.
- The CUMS response-conformance baseline loses its 4 lines.

## Boundaries & Constraints

**Always:**
- No deferred work (owner 2026-11-09). Failures that surface are fixed in the code within this PR, with no workarounds.
- The #1102 pattern (`EmailAddresses.OPENAPI_PATTERN`) stays on every property that has it today, and `EmailAddressesTest` stays green.
- Requests keep rejecting what the pattern rejects. Losing `@Email` is acceptable only because `@Pattern` and `@Size` remain; prove it with a request integration test using the IDN person, plus one invalid address that gets 400.
- A property becomes `required` only when both hold:
  - every 2xx response for it in the CUMS, EMS and PCS integration suites carries it, measured by a temporary recorder that is not committed;
  - reading its producing code paths confirms it is always set.
- Do this per schema and record the evidence per schema in Implementation Notes.
- The frontend MSW validator learns `idn-email`, so the format is checked there too.

**Never:**
- Do not mark a property `required` from code reading alone, or from the tests alone.
- Do not use `!` or `as` to satisfy a type that became non-optional.
- Do not make a nullable property non-nullable here. `required` means the property is present, not that it is non-null.
- Do not change the path-parameter `email` operations (10b owns them).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| IDN request | `POST /users` with email `jürg@bücherstube.example`, ASCII local part required | local part `ü` → 400, as the pattern says; `juerg.brunner@bücherstube.example` → 201 | 400 names email |
| IDN response | user with IDN address read via `getOrCreateInternalUser` | conforms, baseline line gone | — |
| fields gone | `GET /companies?fields=name` | parameter unknown: the spec matcher and frontend check reject it; the server ignores it or returns 400 per the existing unknown-param behaviour | — |

</frozen-after-approval>

## Code Map

- `format: email` sites, 26 in total, listed in the investigation:
  - users-api 3466/3880/3843/3923/4079/4150/4198/4318
  - registrations 533/1504/1539/1654/1802
  - newsletter 1127/1557
  - speakers 2003/2635/2695/2755/2788
  - internal pcs 76 and ems 576
  - Path params at users-api 2293/2350/2385/2428 are out of scope.
- Generator: `ModelUtils.isEmailSchema` keys on `email` only, so `idn-email` drops `@Email` and keeps `@Pattern`/`@Size`.
- Java validator: networknt 2.0.1 supports `idn-email` (`OpenApiContract`, shared-kernel testFixtures).
- `web-frontend/src/test/msw/openapi.ts` `FORMATS` L164-169: add `idn-email`, because unknown formats are skipped at L246.
- `fields`:
  - `companies-api` L251-253/307-313 and `users-api` L396-398/442-448.
  - `CompanyController.java` L77-81, `CompanyQueryService` L67/85/146-217, `UserController.listUsers` L170-172 (unused there).
  - Test `AdvancedQueryIntegrationTest` L282.
  - Shared-kernel `FieldSelector` stays: the gateway `TestResourceController` uses it.
  - ADR-013 L107-111.
- CUMS baseline: `services/company-user-management-service/src/test/resources/response-conformance-baseline.txt`, 4 lines.
- Required candidates named by the owner: `UserResponse.active` (users-api 3493), the merged `UserSettings` from the core 4.9 plan, `RsvpDTO` (partner-meetings 503). The recorder decides the rest.
- Frontend guards: `userAccountApi.ts` L61-65, `AuthContext.tsx` L150-155, `PartnerMeetingRsvpPanel.tsx` L101/113, UserManagement `active` reads.

## Tasks & Acceptance

**Execution:**
- [ ] After the core 4.9 plan is built: set `baseline_revision`, then record the baseline counts.
- [ ] IDN: red integration tests (IDN person create plus an invalid local part), then specs, regenerate, the MSW format, then remove the baseline line.
- [ ] fields: spec, then remove the CUMS projection, the test and the unused controller parameter, then add the ADR-013 line, then remove the 3 baseline lines, then regenerate.
- [ ] Required: write a temporary recorder that is not committed (or an env flag on `ResponseConformance` that dumps per schema and property: present or absent counts). Run the three suites one at a time. For each always-present property confirmed by code reading, add it to `required`. Regenerate, then remove the frontend guards that are now dead and fix the callers properly. Run the suites again with the gate.
- [ ] Docs: coding-standards (response conformance: baseline empty for CUMS), deferred-work entry resolved.

**Acceptance Criteria:**
- Given the CUMS full suite, when it runs, then the response-conformance baseline file has no entries and the run is green. The same holds for EMS and PCS.
- Given the specs, when grepped, then no response or request property that can hold a stored address uses `format: email`. Path params are the exception, left to 10b.
- Given the frontend, when `npm run type-check` and vitest run, then they are green with no new `!` or `as`.

## Implementation Notes

## Plan Change Log

## Review Triage Log

## Design Notes

Work rules on rack:
- One heavy job at a time: never run a Gradle suite, Vitest, Playwright or a `git commit`/`git push` hook in parallel.
- Gradle runs with `--max-workers=4`, Vitest with `--maxWorkers=4`.
- Logs go to `~/.cache/batbern-test-logs/` via `tee`, with `set -o pipefail`.
- Commit on the current branch in conventional-commit steps (tests first where sensible), never `--no-verify`, never push.
- Services are managed only with `make dev-native-*`.

Architecture note:
- **Modules:** specs only, plus CUMS company query and users controller.
- **New dependencies:** none.
- **Endpoints:** none new; one parameter removed from two lists.
- **Reused:** `ResponseConformance`, `ProductionShapedPeople.IDN_EMAIL`.
- **Deviations:** none.

Decisions by the agent under the owner's blanket go (2026-11-09):
- `idn-email` applies to every non-path email property, because each can carry the stored address.
- Built after the core plan, because the `UserSettings` schema changes there.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/49b-cums.log`: expected BUILD SUCCESSFUL with no stale baseline line. Then EMS, then PCS, one at a time.
- `./gradlew :shared-kernel:test --tests '*EmailAddressesTest*'`: expected green.
- `cd web-frontend && npm run generate:api-types && npm run type-check && npx vitest run --maxWorkers=4`: expected green.
