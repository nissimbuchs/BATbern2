---
title: 'Frontend HTTP calls moved into the service layer (17.3b)'
type: 'refactor'
ticket: '3b'
created: '2026-11-08'
status: 'built'
baseline_revision: '7820a614aca557d054026c69c3b01ad1d3f3c806'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'auto'
lenses_ran: ['adversarial']
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 13 frontend files outside `web-frontend/src/services/` make HTTP calls (frozen by 17.3 in `scripts/api-layer-baseline.txt`), against the module-map rule "API calls only in `services/`", and `useFileUpload` takes raw endpoint strings that the path check can only allow-list (2 entries in `scripts/api-paths-allowlist.txt`).

**Approach:** Move every call into a function of the service that owns the resource, have the hooks and components call that function, and make `useFileUpload` take a per-purpose upload function instead of an endpoint string, so both the layer baseline and the useFileUpload allow-list entries end empty. *(Owner 2026-11-08: move the calls and shrink the baseline to zero where possible.)*

## Boundaries & Constraints

**Always:** behaviour, request shapes, React Query keys and error handling of every caller stay as they are; reuse the existing service of the resource before creating one; tests of a moved call go to the service's test with `axios-mock-adapter` on `apiClient` (the existing `userManagementApi.test.ts` pattern); touched hook and component tests stub HTTP at that boundary, never the new service functions, so the 17.0a ratchet falls or stays, never rises.

**Never:** no API or spec change; no new endpoint; no change to the upload contract itself (17.11 and 17.11a–d own that); no `vi.mock` of `@/services/...` in a touched test.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| moved read | e.g. `useUserProfile` loads `/users/me?include=roles,preferences` | same request, same data to the caller | same error surfaced as before |
| moved write | e.g. delete user picture, AI theme-image apply | same request and invalidation | same error surfaced |
| upload by purpose | logo upload in EventInfoTab / CompanyLogoSection, material upload via FileUpload | presign, S3 PUT, confirm against the same endpoints as today | progress and error state unchanged |
| new out-of-layer call | a new file outside `services/` calls `apiClient` | `npm run check:api-paths` fails | CI fails |

</frozen-after-approval>

## Code Map

Baseline at start: `api-layer-baseline.txt` 13 files; `api-paths-allowlist.txt` 2 lines (both `useFileUpload`). Target service per file (all under `web-frontend/src/`):

- `components/organizer/UserManagement/EventsParticipatedTable.tsx:56` GET `/events/registrations` -> `services/api/eventRegistrationService.ts`.
- `components/organizer/UserManagement/UserCreateEditModal.tsx:235` DELETE `/users/{u}/picture` -> `services/api/userManagementApi.ts` (or `userApi.ts`, whichever already holds picture calls).
- `components/shared/Company/CompanyDetailView.tsx:245` GET `/sessions` -> `services/api/sessionApiClient.ts`.
- `contexts/AuthContext.tsx:210` GET `/partners/me` -> `services/api/partnerApi.ts`.
- `hooks/useAiAssist.ts:30,65,72,97,114` event and speaker AI (description, theme-image job start/poll/apply, analyze-abstract) -> new `services/aiAssistService.ts` (`aiPromptService.ts` is prompt admin, a different resource).
- `hooks/useCompanyBatchImport/useCompanyBatchImport.ts:27` POST `/logos/upload-from-url` -> `services/api/companyApi.ts`.
- `hooks/useEventTeaserImages.ts:46` bare `axios.put` to the presigned URL -> `services/eventApiClient.ts`, beside `uploadPhotoToS3` (line ~480), reuse or generalise it.
- `hooks/useFeatureFlags.ts:22` GET `/public/settings/features` -> `services/adminSettingsService.ts` (owns settings).
- `hooks/useFileUpload/useFileUpload.ts:153,320` presign + confirm by endpoint string -> option becomes an upload-target object/functions per purpose (`companyApi` logo presign/confirm, `sessionApiClient` material presign/confirm); callers `organizer/EventPage/EventInfoTab.tsx:82`, `user/CompanyLogoRequest/CompanyLogoSection.tsx:57`, `shared/FileUpload/FileUpload.tsx:124` (and its caller passing `/materials/presigned-url`) pass the purpose. Remove both allow-list lines.
- `hooks/usePublicPartners.ts:16` GET `/partners` -> `services/api/partnerApi.ts`.
- `hooks/useSpeakerBatchImport/useSpeakerBatchImport.ts:57` POST `/users/{u}/profile-picture/upload-from-url` -> `services/api/userApi.ts`.
- `hooks/useUserPortrait.ts:31` GET `/public/users/{u}` -> `services/api/userApi.ts`.
- `hooks/useUserProfile/useUserProfile.ts:40` GET `/users/me?include=...` -> `services/api/userApi.ts`.
- Tests mocking `@/services/api/apiClient` today (ratchet `fe-mock-services`): `EventsParticipatedTable.test.tsx`, `AuthContext.test.tsx` (also `userApi`, `authService`), `useAiAssist.test.ts`, `useEventTeaserImages.test.ts` (also `eventApiClient`, `axios`), `useFeatureFlags.test.ts`, `useFileUpload.test.ts`, `useUserProfile.test.ts`. Move each to `MockAdapter(apiClient)`; S3 PUTs stub on a `MockAdapter(axios)`. `authService` (Amplify) stays a boundary mock.
- Check script and docs: `scripts/check-api-paths.ts` (STALE when a file leaves), `scripts/api-layer-baseline.txt` header, `docs/architecture/module-map.md` API rules (no change of rule, only if it names the frozen list).

## Tasks & Acceptance

**Execution:**
- [x] Services: add the functions listed in the Code Map (new `aiAssistService.ts`), each with a test on `MockAdapter(apiClient)` asserting method, path, params/body and returned data.
- [x] Callers: the 13 files call the service functions; no `apiClient`/`axios` import remains for HTTP in them.
- [x] `useFileUpload` + its 3 callers: per-purpose upload functions; allow-list lines removed.
- [x] Touched tests: off `vi.mock('@/services/...')`, HTTP stubbed at `MockAdapter`; add a test for `UserCreateEditModal` picture delete, `useCompanyBatchImport`, `usePublicPartners`, `useSpeakerBatchImport`, `useUserPortrait` where none exists and the call moved.
- [x] `api-layer-baseline.txt` to header only; `npm run check:test-ratchet -- --update` for the lowered counts.
- [x] Record start/end sizes of both files and the ratchet delta in Implementation Notes.

**Acceptance Criteria:**
- Given the branch, when `npm run check:api-paths` runs, then it passes with an empty layer baseline and an allow-list with no `useFileUpload` line, no larger than at the start.
- Given the touched hooks and components, when their tests and `npm run type-check` / `npm run lint:ci` run, then they pass and `make check-test-ratchet` shows no RAISED line.

## Design Notes

Architecture note: modules: `web-frontend` services, hooks, components, `contexts/AuthContext` · new dependencies: none (`axios-mock-adapter` is already a devDependency) · endpoints: none · reused: `eventRegistrationService`, `userManagementApi`/`userApi`, `sessionApiClient`, `partnerApi`, `companyApi`, `eventApiClient.uploadPhotoToS3`, `adminSettingsService`; new `aiAssistService` because no service owns the event/speaker AI endpoints · deviations: none.

`useFileUpload` shape, golden example (adapt names to the code):

```ts
useFileUpload({ target: logoUploadTarget })      // companyApi: { presign(file), confirm(uploadId) }
useFileUpload({ target: materialUploadTarget(sessionSlug) })
```

## Verification

Rack rules: one heavy job at a time (no Gradle running), vitest `--maxWorkers=4`; logs to `~/.cache/batbern-test-logs/`; memory check before each run (stop above 12 GB).

**Commands:**
- `cd web-frontend && npm run check:api-paths 2>&1 | tee ~/.cache/batbern-test-logs/173b-paths.log` -- expected: pass, layer baseline empty.
- `cd web-frontend && npx vitest run --maxWorkers=4 src/services src/hooks src/contexts src/components/organizer/UserManagement src/components/shared/Company src/components/shared/FileUpload src/components/organizer/EventPage src/components/user/CompanyLogoRequest scripts/check-api-paths.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/173b-vitest.log` -- expected: pass.
- `cd web-frontend && npm run type-check && npm run lint:ci` -- expected: clean.
- `make check-test-ratchet` -- expected: OK, no RAISED.

## Implementation Notes

2026-11-08, implemented on `feature/17-4-17-5-gates`. Story references in code comments and the
baseline header were removed after implementation (no story numbers in code).

**Sizes (measured).** `api-layer-baseline.txt`: 13 entries -> 0 (header only, header text updated).
`api-paths-allowlist.txt`: 2 entries (both `useFileUpload`) -> 0. `npm run check:api-paths`:
"301 resolved, 301 match the spec, 0 allow-listed", exit 0. Ratchet `fe-mock-services`:
193 files / 215 mocks -> 184 files / 203 (-12, after review patch 1), baseline rewritten with `--update`; no other
category changed; `make check-test-ratchet` exit 0, no RAISED. Lowered: `AuthContext.test` 2->1
(authService stays, Amplify boundary), and to 0: `useMiscHooks.test` (review patch 1), `EventsParticipatedTable.test`, `CompanyLogoSection.test`
(2), `useAiAssist.test`, `useEventTeaserImages.test` (also its `vi.mock('axios')`, not counted),
`useFeatureFlags.test`, `useFileUpload.test`, `useUserProfile.test`, `SessionEditModal.test`.

**Where each call went.**
- `EventsParticipatedTable` -> `eventRegistrationService.getRegistrationsForAttendee(username)`.
- `UserCreateEditModal` -> `userAccountApi.removeProfilePictureForUser(username)`. Deviation from the
  Code Map wording: neither `userManagementApi` nor `userApi` held picture calls; `userAccountApi`
  holds the admin picture calls (`uploadProfilePictureForUser`), so per the plan's own rule it went there.
- `CompanyDetailView` -> `sessionApiClient.listSessionsByCompany(companyName, limit = 50)`; the
  `CompanySession` / `CompanySessionSpeaker` types moved with it.
- `AuthContext` -> `partnerApi.getMyPartnerCompany()`; `usePublicPartners` -> `partnerApi.listPublicPartners()`.
- `useAiAssist` -> new `services/aiAssistService.ts` (5 functions). The theme-image seed is still put
  into the URL with `encodeURIComponent` (not axios `params`), so the request is byte-identical.
- `useCompanyBatchImport` -> `companyApi.uploadLogoFromUrl(url, filename)`.
- `useEventTeaserImages` -> reuses `eventApiClient.uploadPhotoToS3` unchanged (same PUT + Content-Type).
- `useFeatureFlags` -> `adminSettingsService.getFeatureFlags()`.
- `usePublicUser`/`useUserPortrait` -> `userApi.getPublicUser`; `useSpeakerBatchImport` ->
  `userApi.uploadProfilePictureFromUrl`; `useUserProfile` -> existing `userApi.getUserProfile(['roles',
  'preferences'])` (same URL: axios keeps the comma unencoded). The hook's private `UserProfile`
  interface was replaced by `UserProfileResponse` (the hook has no caller outside its test).
- `useFileUpload`: option `uploadEndpoint` replaced by a required `target: FileUploadTarget`
  (`{ purpose, presign, confirm }`, new `types/fileUpload.types.ts`). Targets: `companyApi.logoUploadTarget`,
  `sessionApiClient.materialUploadTarget`. Deviation from the golden example: the material target is a
  constant, not `materialUploadTarget(sessionSlug)`, because `/materials/presigned-url` is not
  session-scoped. The S3 PUT stays an XHR in the hook (progress events); the check does not scan XHR.
  The phase-1 error log field `endpoint` became `purpose`. Callers: `EventInfoTab` and
  `CompanyLogoSection` pass `logoUploadTarget`; `FileUpload` takes `uploadTarget` (default
  `logoUploadTarget`, so `EventForm`/`CompanyForm` are unchanged); `SessionEditModal` passes
  `materialUploadTarget`.

**Tests.** New service tests on `MockAdapter(apiClient)`: `aiAssistService.test`, `adminSettingsService.test`,
`companyApi.upload.test`, `sessionApiClient.http.test`, `userAccountApi.picture.test` (separate files
because `companyApi.test`, `sessionApiClient.test`, `userAccountApi.test` mock the apiClient module), plus
new cases in `userApi.test`, `eventRegistrationService.test`, `partnerApi.test`. New hook/component
tests: `UserCreateEditModal.test`, `useCompanyBatchImport.test`, `useSpeakerBatchImport.test`;
`usePublicPartners`/`useUserPortrait` were already covered in `useMiscHooks.test` and moved to
MockAdapter there; AuthContext got 3 `/partners/me` cases. Skip-Auth is asserted with a test request
interceptor registered after apiClient's (axios runs it first), because apiClient strips the marker
before the adapter sees it.

**Touched beyond the Code Map, and why.** `CompanyLogoSection.test` and `SessionEditModal.test` mocked
`companyApi` / `sessionApiClient` with factories lacking the new target exports, so they broke
(`No "materialUploadTarget" export is defined on the mock`); both were moved to MockAdapter instead of
extending the service mocks. `FileUpload.test` got 2 cases for the target pass-through.

**Found, not fixed (test-environment only).** `axios-mock-adapter` builds its errors with the CJS
`axios`, so `error instanceof AxiosError` (ESM) is false under Vitest; `transformError` in the class
clients then passes the raw error through. Production is not affected. `SessionEditModal`'s delete-error
test therefore uses `networkError()` and asserts "Network Error", which is the same in both.

**Verification (run, logs in `~/.cache/batbern-test-logs/`).** `npm run check:api-paths` exit 0
(`173b-paths.log`); Vitest, the plan's set plus `SessionEditModal.test` and the 16 other tests that mock
one of the touched services: 233 files, 2905 passed, 36 skipped, 19 todo, 0 failed (`173b-vitest.log`);
`npm run type-check` exit 0; `npm run lint:ci` exit 0; Prettier `--check` clean on all changed files;
`make check-test-ratchet` exit 0. Not run: the full Vitest suite (only the affected directories and the
tests that mock a touched service), Playwright, and a click-through in a running app.

**Docs.** `module-map.md` does not name the frozen list, so unchanged (plan). Pure refactor, no
user-visible change: commit as `refactor(...)` with `[no-doc]`.

## Plan Change Log

## Review Triage Log

Quick review (one adversarial lens, diff vs `git show HEAD:`, 2026-11-08): no production defect; every
moved call keeps method, path, params, headers (Skip-Auth, X-Attendee-Username), unwrapping, error
handling and query key. Three test findings, all patched:

1. medium, patched: `useMiscHooks.test` still had `vi.mock('@/services/api/sessionApiClient')` for
   useAssignSpeaker/useRemoveSpeaker, against "no `vi.mock` of `@/services/...` in a touched test".
   Moved to MockAdapter (POST/DELETE asserted on the wire); ratchet 185/204 -> 184/203, `--update`.
2. low, patched: `CompanyLogoSection.test` mocks `useFileUpload` (the hook, pre-existing), so the new
   `target: logoUploadTarget` was asserted nowhere. The mock now records its options; new case asserts
   the logo target.
3. low, patched: `useUserProfile.test` 401 case now runs the real apiClient interceptor, which redirects
   to `/login`. The test sets `setNavigationCallback` and asserts the redirect instead of a silent
   `window.location` side effect.

Re-verified after the patches: plan Vitest set 217 files, 2648 passed, 0 failed (`173b-v2-vitest.log`);
type-check, lint:ci exit 0; `make check-test-ratchet` exit 0; `check:api-paths` 301/301, 0 allow-listed.
