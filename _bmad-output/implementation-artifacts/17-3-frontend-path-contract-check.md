# Story 17.3: Frontend path contract check

Status: ready-for-dev

## Story

As a **developer changing the API or the frontend**,
I want **CI to fail when the frontend calls a method and path that no OpenAPI spec defines**,
so that **drift like `getPublishPreview` (a call to a path that never existed) cannot ship again, and the breaking API stories 17.6 to 17.11 prove the frontend followed every change**.

> Source: `docs/prd/epic-17-architecture-hardening.md` → Story 17.3; action list item 2.
> Depends on: **17.1** (removes `getPublishPreview` and the two dead specs, so the check starts clean). Ship in the same PR as 17.1 if the owner agrees.
> Used by: 17.6, 17.9, 17.10, 17.11 (their acceptance criteria name this check as the guard).

## Architecture note

- **Modules:** CI (`scripts/ci/`, `.github/workflows/build.yml`), frontend service layer (read-only scan), plus the fixes for the initial findings (frontend `services/`, `hooks/`).
- **New dependencies:** none. Python 3 + PyYAML, as `scripts/ci/check-zap-spec-coverage.py` and `access-control-check.py` already use.
- **Endpoints:** none added or changed. Initial findings are fixed by deleting dead callers or pointing them at an existing endpoint, never by adding an endpoint in this story.
- **Reused:** the pattern of `check-zap-spec-coverage.py` (fails on missing *and* stale entries) and `access-control-check.py` + `test-access-control-check.py` (script with its own test, run in CI); spec loading as in those scripts.
- **Deviations from the map:** none. The check also makes the map's rule "API calls only in `services/`" visible (AC5).

## Acceptance Criteria

1. **AC1 — the check.** `scripts/ci/check-frontend-api-paths.py` loads every `docs/api/*.openapi.yml` except `_*.yml`, builds the set of (method, path) with path parameters as single-segment wildcards, and scans every `.ts`/`.tsx` under `web-frontend/src` except tests (`*.test.*`, `__tests__/`), `mocks/` and `types/generated/`. For each HTTP call (`apiClient`, `axios` and any `*Client` receiver; `get|post|put|patch|delete`) it resolves the first argument to a path:
   - string and template literals; `${...}` becomes a wildcard segment;
   - same-file `const NAME = '...'` / template constants (`EVENTS_API_PATH`, `USER_API_PATH`, ...);
   - same-file arrow helpers returning a template (`const base = (eventCode) => \`...\``, `LIVE_TIMING_PATH(eventCode)`), and class helpers like `this.teaserBasePath(eventCode)` where the body is a single template return;
   - strips a leading `/api/v1`, query strings and trailing `/`; ignores absolute URLs and presigned upload URLs (`uploadUrl`).
   The method counts: `DELETE /x` does not match a spec that only has `GET /x`.
2. **AC2 — results and exit code.** The script prints, and exits 1 on any of:
   - **NOT IN SPEC**: a resolved call with no matching (method, path);
   - **UNRESOLVED**: a call whose path it cannot resolve and that is not in `scripts/ci/frontend-api-paths-allowlist.txt` (one line per call: `file:method:expression  # reason`);
   - **STALE**: an allowlist entry that matches no call any more.
   It ends with one summary line: `Frontend API calls: N resolved, M match the spec, K allow-listed.` Exit 0 otherwise.
3. **AC3 — tests for the check.** `scripts/ci/test-check-frontend-api-paths.py` runs the scanner on fixture sources and a fixture spec and asserts: an unknown path fails; a parameterized path matching `{eventCode}` passes; a path literal `me` or `_global` in a parameter position passes; a constant-resolved path passes; a helper-resolved path passes; query string stripped; wrong method fails; an absolute or presigned URL is ignored; a fixture reproducing `getPublishPreview` (`/events/${c}/publish/${phase}/preview` against a spec with only `/events/{eventCode}/publish/preview`) fails with NOT IN SPEC; a stale allowlist entry fails.
4. **AC4 — runs where it cannot be skipped.**
   - A step in the `frontend-static` job of `.github/workflows/build.yml` runs the test, then the check. `frontend-static` feeds the required `build-frontend` check, so a violation blocks the merge.
   - `frontend-changes` today triggers only on `^web-frontend/`. Extend it to `^docs/api/` as well, so a spec-only PR that removes an endpoint the frontend still calls runs the check (and the existing type-check and `check:api-types` steps). Verify on the PR that a docs-only change runs `frontend-static`.
   - Local: `make check-frontend-api-paths` (root Makefile) runs the same command.
5. **AC5 — calls outside the service layer are visible and frozen.** The script also lists HTTP calls in files outside `web-frontend/src/services/`. Today there are 15 such files (2026-11-07: 11 hooks, 3 components, 1 context): `hooks/useSpeakerBatchImport/useSpeakerBatchImport.ts`, `hooks/usePublicPartners.ts`, `hooks/useErrorToast/useErrorToast.ts`, `hooks/useAiAssist.ts`, `hooks/useFeatureFlags.ts`, `hooks/useUserPortrait.ts`, `hooks/useEventTeaserImages.ts`, `hooks/useUserProfile/useUserProfile.ts`, `hooks/useCompanyBatchImport/useCompanyBatchImport.ts`, `hooks/useFileUpload/useFileUpload.ts`, `hooks/useNotifications/useNotifications.ts`, `components/organizer/UserManagement/EventsParticipatedTable.tsx`, `components/organizer/UserManagement/UserCreateEditModal.tsx`, `components/shared/Company/CompanyDetailView.tsx`, `contexts/AuthContext.tsx` (re-count with the script; the list above came from a grep and may include a non-API `.get`). They go into `scripts/ci/frontend-api-layer-baseline.txt`. A **new** file outside `services/` with an HTTP call fails the check; a baseline entry whose file no longer calls HTTP fails as STALE, so the list can only shrink. Moving them into `services/` is not this story.
6. **AC6 — initial findings resolved, not allow-listed away.** The prototype run on 2026-11-07 (method-aware, wildcard params, after removing the 17.1 specs) found these calls matching no spec and no backend mapping (grep of `services/*/src/main/java` and `api-gateway`):

   | Call | Caller | First look |
   |---|---|---|
   | `DELETE /events/{c}/publish/schedule` | `publishingService.cancelAutoPublish` ← `usePublishing` | spec has the path, not the method |
   | `GET /users/me/activity` | `userAccountApi.getUserActivity` ← `useUserAccount` | no backend mapping found |
   | `POST /auth/logout` | `authApi.logout` | logout runs through Amplify; check whether `authApi.logout` has any caller |
   | `POST /topics/recalculate-staleness` | `topicService.recalculateStaleness` | no caller found outside the service |
   | `PUT /topics/{id}/override-staleness` | `topicService.overrideStaleness` ← `useTopics.useOverrideStaleness` | check whether any component uses the hook |
   | `PUT /notifications/read` | `notificationsApi.markNotificationsAsRead`, `hooks/useNotifications/useNotifications.ts` | `@/hooks/useNotifications` resolves to `hooks/useNotifications.ts`, so the directory variant may be dead |

   For each, the story records which case it is and acts:
   - **dead caller** (no component reaches it): delete the call chain (service function, hook, tests);
   - **live caller, endpoint exists elsewhere**: point it at the spec'd endpoint, with a test;
   - **live caller, no endpoint, user-visible failure**: record as a bug in a GitHub issue with evidence (the button that fails), allow-list the call with the issue number, and tell the owner. Do not build the endpoint here.
   After this story the allowlist contains only calls the scanner genuinely cannot resolve (expected: generic `url` variables in `eventApiClient` and `companyApi`, `uploadEndpoint` in `useFileUpload`), each with a reason.
7. **AC7 — counted.** The Dev Agent Record holds the summary line on develop before the fix and after, and the final allowlist and baseline sizes. Measured with the prototype on 2026-11-07: 684 files scanned, 306 calls resolved, 282 match the spec, 10 unresolved; the 24 non-matches shrink to the 6 above once literal segments match parameters and helper-built paths are resolved.

## Tasks / Subtasks

- [ ] **T1 RED (AC3)** — write `test-check-frontend-api-paths.py` with fixtures first (fixtures under `scripts/ci/testdata/frontend-api-paths/`); it fails because the script does not exist.
- [ ] **T2 GREEN (AC1, AC2)** — write `check-frontend-api-paths.py`; make the tests pass. Keep it dependency-free beyond PyYAML; one file, readable, no framework.
- [ ] **T3 Run on the repo (AC6, AC7)** — record the before-numbers. Classify each finding (grep the call chain up to a rendered component; `App.tsx` routes decide reachability). Fix per AC6, each fix with its test. Create the allowlist with reasons.
- [ ] **T4 Layer baseline (AC5)** — generate `frontend-api-layer-baseline.txt` from the script; review it by hand.
- [ ] **T5 CI and Make (AC4)** — `frontend-static` steps; extend `frontend-changes` to `^docs/api/`; Makefile target. Check that `build-frontend` still reports correctly when `frontend-static` is skipped (it `needs` it).
- [ ] **T6 Docs** — one line in `docs/architecture/module-map.md` API rules ("CI checks every frontend call against `docs/api/`: `scripts/ci/check-frontend-api-paths.py`") and in `CLAUDE.md` → Type Sharing / API Development; `docs/deployment/cicd-pipeline-guide.md` lists the new step.
- [ ] **T7 Verify** — `python3 scripts/ci/test-check-frontend-api-paths.py`; `python3 scripts/ci/check-frontend-api-paths.py` exits 0; Vitest for files touched in T3; on the PR, confirm the step ran (`gh run view`). Mutation check: temporarily change one service path to a typo and confirm the check fails locally; revert.

## Dev Notes

### What exists today (verified 2026-11-07)

- **HTTP calls:** almost all go through the default export of `services/api/apiClient.ts` (axios instance, `baseURL` set to `/api/v1` at runtime by `updateApiClientConfig`), so paths in code are **relative to `/api/v1`**. A few use plain `axios` for presigned S3 PUTs (`eventApiClient.ts:481`, `useEventTeaserImages.ts:46`) and must be ignored. `devEmailService.ts` uses `fetch` against local dev ports (`CUMS_BASE`, `EMS_BASE`, `PCS_BASE`): absolute URLs, ignore.
- **Path constants are per file**, not imported (`EVENTS_API_PATH`, `USER_API_PATH`, `TOPICS_API_PATH`, ... each declared in the file that uses it), which is why same-file resolution is enough. A function constant exists: `LIVE_TIMING_PATH = (eventCode) => \`/events/${eventCode}/live-timing\`` in `liveTimingService.ts`.
- **Helper-built paths** the prototype could not resolve: `qnaService.ts` (`base(eventCode, sessionSlug)`), `mailMessageService.ts` (`base(eventCode)`), `liveTimingService.ts`, `eventApiClient.ts` (`this.teaserBasePath(eventCode)`, and a generic `url` variable), `companyApi.ts` (`url`), `useFileUpload.ts` (`uploadEndpoint` parameter).
- **Specs:** servers are `https://api.batbern.ch/api/v1` or `/api/v1` (`partners-api`, `partner-meetings-api`); spec paths never include `/api/v1`. `_shared.openapi.yml` has no paths.
- **CI:** `frontend-static` (build.yml ~534) runs `npm ci`, "Verify API types are up-to-date", lint; `build-frontend` (~600) `needs: [frontend-changes, test-frontend, frontend-static]` and is one of the 8 required checks. `frontend-changes` (~424) sets `run=true` only for `^web-frontend/` changes (~486). The `verify-hooks` job runs `check-zap-spec-coverage.py` on every PR but is **not** required, so it is the wrong place for a gate.
- MSW handlers in `src/mocks/handlers/` contain paths too (e.g. `/api/v1/events/batch_registrations`); they are test doubles, excluded.

### Design choice: regex in Python, not the TypeScript compiler

Matches the existing `scripts/ci/*.py` checks and runs without `npm ci`. The prototype resolved 306 of 316 calls with simple rules. If the regex cannot handle a pattern cleanly, prefer adding the call to the allowlist with a reason over growing the parser; if the allowlist passes ~15 entries, stop and raise it with the owner (a TS-AST version via the already-installed `typescript` package is the fallback).

### Rules that bite

- **A PR to `develop` deploys to production.** Push, then ask the owner before opening the PR.
- The `build.yml` change is a workflow change: pushing `.github/workflows/build.yml` has failed with 500 via git before (see memory "CI arm64 runner switch"); use the Contents API if it happens.
- Do not run Vitest in parallel with the Gradle suites of 17.1 on rack.
- `frontend-changes` widening means a spec-only PR now runs the frontend static job (~2 minutes). Intended.

### Testing standards

- The Python test is a plain script with asserts and a non-zero exit on failure, like `test-access-control-check.py`.
- Frontend fixes in T3: Vitest with RTL; deleting a hook deletes its tests.

### References

- `docs/prd/epic-17-architecture-hardening.md` → Story 17.3, rules for every story
- `docs/plans/architecture-review-2026-11-actions.md` → item 2, item 8 (gates)
- `docs/architecture/module-map.md` → Placement (API calls only in `services/`)
- `scripts/ci/check-zap-spec-coverage.py`, `scripts/ci/access-control-check.py`, `scripts/ci/test-access-control-check.py`
- `.github/workflows/build.yml` → `frontend-changes`, `frontend-static`, `build-frontend`
- Prototype used for the numbers in AC6/AC7 (not committed): regex scan, 2026-11-07

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

- 2026-11-07 (create-story, John): baseline measured with a prototype scan; 6 calls with no spec and no backend mapping found and listed in AC6; 15 files outside `services/` make HTTP calls (AC5); `frontend-changes` does not trigger on `docs/api/` (AC4).

### File List
