# Notes: Frontend path contract check in CI (17.3)

Verified 2026-11-07. Carried over from the v6 story file `17-3-frontend-path-contract-check.md` (John, 2026-11-07, redesigned to TypeScript on Winston's request). The builder re-checks line numbers; the facts are what matter.

## Architecture note (draft, confirm in the plan)

- **Modules:** frontend test infrastructure (`web-frontend/src/test/contract/`), a frontend CI script (`web-frontend/scripts/check-api-paths.ts`), CI (`.github/workflows/build.yml`, root `Makefile`), plus the fixes for the initial findings (frontend `services/`, `hooks/`).
- **New dependencies:** `yaml` as an explicit devDependency of `web-frontend` (today only transitive). No TS runner: Node 22 runs `.ts` with type stripping.
- **Endpoints:** none added or changed.
- **Reused:** the pattern of `scripts/ci/check-zap-spec-coverage.py` (fails on missing *and* stale entries, prints one summary line); Vitest.
- **Deviations from the map:** none. The check also makes the map's rule "API calls only in `services/`" visible (criterion 6).

## Proposed file names

- Matcher: `web-frontend/src/test/contract/specMatcher.ts`, tests `specMatcher.test.ts`, fixtures under `web-frontend/src/test/contract/__fixtures__/`.
- Scan: `web-frontend/scripts/check-api-paths.ts` (npm script `check:api-paths`), tests `scripts/check-api-paths.test.ts`.
- Allow-list: `web-frontend/scripts/api-paths-allowlist.txt`, one line per call: `file:method:expression  # reason`.
- Layer baseline: `web-frontend/scripts/api-layer-baseline.txt`.
- Make target: `make check-frontend-api-paths`.
- Use `.ts` import extensions and no TS-only runtime syntax (enums, namespaces), so `node` runs the script without a build.

## Scan rules (from the v6 criteria)

- Receivers: `apiClient`, `axios` and any `*Client`; methods `get`, `post`, `put`, `patch`, `delete`.
- In template literals, each `${...}` becomes a wildcard segment.
- Query strings written literally in the path are passed to the matcher.
- "No backend mapping" means: grep of `services/*/src/main/java` and `api-gateway` finds no mapping.

## What exists today

- **HTTP calls** almost all go through the default export of `services/api/apiClient.ts` (axios, `baseURL` set to `/api/v1` at runtime by `updateApiClientConfig`), so paths in code are relative to `/api/v1`.
- **Calls to ignore:**
  - plain `axios` presigned S3 PUTs (`eventApiClient.ts:481`, `useEventTeaserImages.ts:46`);
  - `devEmailService.ts`, which uses `fetch` against local dev ports.
- **Path constants** are declared per file, not imported (`EVENTS_API_PATH`, `USER_API_PATH`, `TOPICS_API_PATH`, ...), so same-file resolution is enough. One function constant exists: `LIVE_TIMING_PATH = (eventCode) => \`/events/${eventCode}/live-timing\`` in `liveTimingService.ts`.
- **Helper-built paths** the prototype could not resolve:
  - `qnaService.ts` (`base(eventCode, sessionSlug)`);
  - `mailMessageService.ts` (`base(eventCode)`);
  - `liveTimingService.ts`;
  - `eventApiClient.ts` (`this.teaserBasePath(eventCode)`, and a generic `url` variable);
  - `companyApi.ts` (`url`);
  - `useFileUpload.ts` (`uploadEndpoint` parameter).

  Expected final allow-list: the generic `url` variables in `eventApiClient` and `companyApi`, and `uploadEndpoint` in `useFileUpload`.
- **Specs:** servers are `https://api.batbern.ch/api/v1` or `/api/v1` (`partners-api`, `partner-meetings-api`). Spec paths never include `/api/v1`. `_shared.openapi.yml` has no paths.
- MSW handlers in `src/mocks/handlers/` contain paths too. They are test doubles, so they are excluded.

## CI wiring

- `frontend-static` (build.yml ~534) runs `npm ci`, "Verify API types are up-to-date" and lint. Add the two Vitest files and `npm run check:api-paths` after `npm ci`.
- `build-frontend` (~600) has `needs: [frontend-changes, test-frontend, frontend-static]` and is one of the 8 required checks. Check that it still reports correctly when `frontend-static` is skipped.
- `frontend-changes` (~424) sets `run=true` only for `^web-frontend/` (~486). Extend it to `^docs/api/`, then verify on the PR that a docs-only change runs `frontend-static`. This makes a spec-only PR run the frontend static job, about 2 minutes, which is intended.
- `verify-hooks` runs `check-zap-spec-coverage.py` on every PR but is **not** required, so it is the wrong place for a gate.
- Pushing `.github/workflows/build.yml` via git has failed with a 500 before (memory "CI arm64 runner switch"). Use the Contents API if that happens.
- `node --version` in CI must be 22, because the step relies on type stripping.

## Calls outside `services/` (grep 2026-11-07; re-count with the script, one may be a non-API `.get`)

- `hooks/useSpeakerBatchImport/useSpeakerBatchImport.ts`, `hooks/usePublicPartners.ts`, `hooks/useErrorToast/useErrorToast.ts`, `hooks/useAiAssist.ts`, `hooks/useFeatureFlags.ts`, `hooks/useUserPortrait.ts`, `hooks/useEventTeaserImages.ts`, `hooks/useUserProfile/useUserProfile.ts`, `hooks/useCompanyBatchImport/useCompanyBatchImport.ts`, `hooks/useFileUpload/useFileUpload.ts`, `hooks/useNotifications/useNotifications.ts`
- `components/organizer/UserManagement/EventsParticipatedTable.tsx`, `components/organizer/UserManagement/UserCreateEditModal.tsx`, `components/shared/Company/CompanyDetailView.tsx`
- `contexts/AuthContext.tsx`

## Initial findings (prototype, 2026-11-07, after removing the 17.1 specs)

| Call | Caller | First look |
|---|---|---|
| `DELETE /events/{c}/publish/schedule` | `publishingService.cancelAutoPublish` ← `usePublishing` | the spec has the path, not the method |
| `GET /users/me/activity` | `userAccountApi.getUserActivity` ← `useUserAccount` | no backend mapping found |
| `POST /auth/logout` | `authApi.logout` | logout runs through Amplify; check whether `authApi.logout` has any caller |
| `POST /topics/recalculate-staleness` | `topicService.recalculateStaleness` | no caller found outside the service |
| `PUT /topics/{id}/override-staleness` | `topicService.overrideStaleness` ← `useTopics.useOverrideStaleness` | check whether any component uses the hook |
| `PUT /notifications/read` | `notificationsApi.markNotificationsAsRead`, `hooks/useNotifications/useNotifications.ts` | `@/hooks/useNotifications` resolves to `hooks/useNotifications.ts`, so the directory variant may be dead |

To classify each one, grep the call chain up to a rendered component; the routes in `App.tsx` decide reachability.

## Prototype numbers (for the "before" line)

- 684 files scanned, 306 calls resolved, 282 match the spec, 10 unresolved.
- The 24 non-matches shrink to the 6 above once literal segments match parameters and helper-built paths are resolved.

## Design choice

- Port the regex rules of the 2026-11-07 Python prototype, which resolved 306 of 316 calls with simple rules.
- If the regex cannot handle a pattern cleanly, allow-list the call with a reason rather than grow the parser.
- Above about 15 allow-list entries, stop and ask the owner. The fallback is the TypeScript compiler API through the installed `typescript` package.
- One smoke test loads the real `docs/api/` and asserts more than 300 operations. All other tests use fixtures.

## Docs to touch

- `docs/architecture/module-map.md`, API rules: one line saying CI checks every frontend call against `docs/api/` (`web-frontend/scripts/check-api-paths.ts`).
- `CLAUDE.md` → Type Sharing / API Development: the same line.
- `docs/deployment/cicd-pipeline-guide.md`: the new step.

## Verification

- The two Vitest files pass, and `npm run check:api-paths` exits 0.
- On the PR, confirm the step ran (`gh run view`).
- Mutation check: change one service path to a typo locally, confirm the check fails, then revert.
- Do not run Vitest in parallel with 17.1's Gradle suites on rack.
