---
title: 'Frontend MSW harness (17.0c1)'
type: 'feature'
ticket: '0c1'
created: '2026-11-08'
status: done
baseline_revision: 'bf8e0226a3cb9572b37bb86e9be4f9143c276b3f'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/plans/test-fidelity-proposal-2026-11.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Frontend component tests replace our own services and hooks with `vi.mock` (193/121 files in the ratchet), so a wrong path, query or response shape passes every test. There is no shared network harness: five tests build their own `setupServer` with wildcard handlers that accept any request.

**Approach:** One MSW harness in `web-frontend/src/test/msw/`. It answers every operation in `docs/api/*.openapi.yml` with that operation's spec example by default, validates every request with the 17.3 `specMatcher` (a path, method or query no spec defines fails with the matcher's message), and lets a test override an operation by `operationId` with data typed from `src/types/generated/`. Real i18next with `en` stays the test default. The five ad-hoc servers move onto it. Every JSON response in the specs gets an example, captured from the local anonymized database.

**Decisions (owner, 2026-11-08):** (1) Every operation's JSON 2xx response gets an example in its spec, taken from the local anonymized database (captured through the locally running services, so the shape is the real response). (2) The harness starts for every test in `src/test/setup.ts`. (3) One story, plan length accepted.

## Boundaries & Constraints

**Always:** A request outside the specs fails the test (the harness throws `AssertionError`-like error with the matcher reason, not a 500 the code under test could swallow). Overrides are keyed by `operationId` and their response type is `operations[id]['responses'][status]['content']['application/json']`: a wrong shape fails `npm run type-check`. The harness and its test are type-checked in CI. The ratchet does not rise.

**Never:** No `docs/api/internal/` operations (not frontend-callable). No conversion of the 10 worst files (0c2) or of other `vi.mock` tests beyond the five ad-hoc MSW servers. No personal data in a committed example: e-mail addresses become `@example.com`, person names and free text (notes, bios, commentary) are replaced by synthetic values; the owner's own preserved accounts never appear. No capture against staging/production.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Unknown path | `GET /api/v1/no-such-thing` through `apiClient` | test fails with `no spec defines the path /no-such-thing` | thrown by the harness |
| Wrong method / query | `DELETE` on a GET-only path; `limit=1000` above a spec maximum | fails with the matcher's method / query reason | same |
| Default answer | spec'd `GET /api/v1/companies` with no override | 200 with the spec example | none |
| Typed override | `api.on('listCompanies', () => ({ data: [...] }))` | that body returned; a missing required field is a type error | type-check |
| Base path | requests via `/api/...` (test baseURL) and `/api/v1/...` | both resolve to the same operation | none |

</frozen-after-approval>

## Code Map

- `web-frontend/src/test/contract/specMatcher.ts` -- `loadSpecIndex(dir)`, `matchOperation(index, method, url)` → `{ok, operationId}` or `{ok:false, kind, reason}`; strips origin and `/api/v1` only (`normalizePath`): add bare `/api` (test baseURL). Reads `docs/api` top level only (internal skipped). Node `fs`, works under vitest (`specMatcher.test.ts:125`).
- `src/types/generated/*.types.ts` (17 files, openapi-typescript 7.13) -- each exports `operations` keyed by operationId. No generated file for `event-notifications-api`, `gateway-config-api`.
- `src/services/api/apiClient.ts:70` -- `axios.create({ baseURL: '/api' })` in tests; real runtime base `<host>/api/v1`.
- `src/test/setup.ts` -- global: real i18n `en` (:42-73), Amplify mocked, fetch kept. The server is started here.
- `vite.config.ts:414-470` -- jsdom, `setupFiles`, `maxWorkers: 3`; `msw` pre-bundled (:562).
- Ad-hoc servers to move: `src/services/api/companyApi.search.test.ts`, `partnerApi.search.test.ts`, `TopicSelectionOverlay.search.test.tsx`, `MailComposer.test.tsx`, `MailComposerOwnText.test.tsx` (+ check `PartnerList.search.test.tsx`).
- `src/mocks/browser.ts`, `src/mocks/handlers/index.ts` -- dead (no importer): delete.
- `tsconfig.json:40-47` excludes `src/test/**`; `tsconfig.scripts.json` includes only `src/test/contract/*.ts` -- add `src/test/msw/**` so `type-check` covers the harness.
- `scripts/test-ratchet.ts` -- frontend categories count own-code `vi.mock`; 115 files stub `react-i18next` uncounted.
- Measured: 320 operations; 276 return JSON; 28 carry an inline example (component-level examples not counted).
- Local data: container `batbern-dev-postgres` (anonymized snapshot, `scripts/dev/load-anonymized-snapshot.sh`); dev services via `make dev-native-up` (gateway :8000), organizer token `~/.batbern/staging-organizer.json` (`scripts/auth/refresh-token.sh staging`). PII lessons: memory `project_anonymized_db_snapshot` (match by value; free text leaks).
- Spec tooling that reads examples: `npm run lint:api` (Spectral `oas3-valid-media-example`, baseline), backend `ContractServer.respondWithExample` and `ProviderContract.assertExamplesMatchSchema` (shared-kernel) -- new examples must satisfy all of them.

## Tasks & Acceptance

**Execution:**
- [ ] `src/test/msw/server.ts` -- `setupServer` with one catch-all handler per method that runs `matchOperation`, then the override for its `operationId`, else the default answer; `onUnhandledRequest: 'error'`.
- [ ] `src/test/msw/api.ts` -- typed `api.on(operationId, resolver, {status?})` / `api.calls(operationId)` / reset per test; types from `operations`.
- [ ] `scripts/dev/capture-spec-examples.ts` -- against local dev only (refuses any non-localhost base URL): for every GET with a JSON 2xx, resolve path parameters from list responses, call it, trim lists to 2 items; for writes, take the matching resource from a GET; scrub (e-mails to `@example.com`, names/free text synthetic, value-based check against the DB's real e-mails/usernames); write `examples: default` into the spec with the `yaml` Document API (comments kept). Operations it cannot capture get a hand-written example.
- [ ] `docs/api/*.openapi.yml` -- the examples; `npm run lint:api` no new findings; a PII grep of the diff (emails not `@example.com`, known real usernames) is empty.
- [ ] `src/test/msw/examples.test.ts` -- guard: every operation with a JSON 2xx has an example, and each validates against its schema.
- [ ] `specMatcher.ts` -- strip bare `/api`; unit cases.
- [ ] `src/test/setup.ts` -- start/reset/close the server globally.
- [ ] `src/test/msw/harness.test.ts` -- every matrix row; positive control first.
- [ ] `tsconfig.scripts.json` (or a dedicated tsconfig chained into `type-check`) -- include `src/test/msw/**`; a deliberately wrong override in a `// @ts-expect-error` line proves the type check.
- [ ] The five ad-hoc MSW tests -- onto the harness; `src/mocks/` deleted.
- [ ] `scripts/test-ratchet.ts` -- new category `fe-mock-i18n` (`vi.mock('react-i18next')`), baseline `--update` adds it at today's count.
- [ ] Full Vitest run -- every failure the global harness exposes is fixed: a wrong call in code (test first) or a test relying on an unspecified path.
- [ ] `coding-standards.md` "Test fidelity rules" (frontend harness usage), `project-context.md` testing section.

**Acceptance Criteria:**
- Given the harness test, when it calls a path no spec defines, then it fails with the matcher's message.
- Given an override missing a required field, when `npm run type-check` runs, then it fails (proved by `@ts-expect-error`).
- Given the full frontend suite, when run, then it passes with the harness active globally, and `make check-test-ratchet` shows no RAISED line.

## Design Notes

Architecture note: modules: web-frontend test infrastructure, specMatcher, ratchet · new dependencies: none (msw already present) · endpoints: none · reused: 17.3 `specMatcher`, generated `operations` types, existing global i18n setup · deviations: none.

Violations exposed and fixed here: five unvalidated ad-hoc MSW servers, dead `src/mocks/`, harness outside type-check, uncounted i18n stubs. Frozen: the 115 `react-i18next` stubs (0c2 and the touch rule). Ratchet start: fe-mock-services 193/263, fe-mock-hooks 121/248, fe-mock-components 76/175.

## Verification

Rack rules: one heavy job at a time (no Vitest while Gradle runs), Vitest `--maxWorkers=4`, logs in `~/.cache/batbern-test-logs/0c1-*`.

**Commands:**
- `cd web-frontend && npx vitest run --maxWorkers=4 src/test/msw src/test/contract` -- expected: pass.
- `cd web-frontend && npx vitest run --maxWorkers=4` (full suite, tee to log) -- expected: 0 failures.
- `cd web-frontend && npm run type-check && npm run lint && npm run format:check` -- expected: clean.
- `make check-test-ratchet` -- expected: no RAISED.

## Review Triage Log

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| 1 | `tsconfig.test-harness.json` lists files by hand; `listAllPages.test.ts`, `eventApiClient.test.ts`, `usePartnerDetail.test.ts` use `api.on` but are not type-checked | medium | grep: 9 files outside `src/test/` import the harness, 6 listed. Intent "a wrong shape fails type-check" holds only for listed files | patch |
| 2 | `api.on` without `status` types the body as the union of all 2xx bodies | low | Only `inviteSpeaker` (200, 201) has two JSON 2xx; fix needs a new type helper | reject (unlikely, not a direct correction) |
| 3 | `defaultResponse` / `harness()` throw inside the resolver, MSW answers 500 | low | MSW 2 converts a resolver exception into a 500 response the code can swallow; `examples.test.ts` covers the missing-example case suite-wide only | patch |
| 4 | Violations after the last `afterEach` are discarded at `server.close()` | low | `setup.ts` asserts only in `afterEach`; `afterAll` closes without asserting | patch |
| 5 | Scrubber returns any all-caps value before the name rules (`lastName: "MEIER"` passes) | medium | `capture-spec-examples.ts` `field()`: enum shortcut precedes first/last/name rules; `leaks()` misses lone last names. Current diff is clean (checked) | patch |
| 6 | Partner sort control does nothing; decision recorded only in a code comment | medium | Behaviour predates this change (PCS never sorted); needs an owner decision | defer |
| 7 | `useCompany` keys the cache on `options` that no longer change the request | low | `queryKey: ['company', name, options]`; 4 callers pass `expand: ['logo']`, so the same response is fetched and cached twice | patch |
| 8 | `allPages` branches of `useUserList` / `useCompanies` and the modals untested | medium | Only `listAllUsers` / `getAllCompanies` tested; the modals were the code that broke the spec | patch |
| 9 | Capture script inserts examples as text, not via the `yaml` Document API | false | Plan's purpose (comments kept, valid YAML) is met: specs parse in `examples.test.ts`, `lint:api` at baseline | reject |
