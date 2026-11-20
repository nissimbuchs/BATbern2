---
title: 'The Companies tab finds and merges duplicates'
type: 'feature'
ticket: '6'
created: '2026-11-20'
status: 'done'
baseline_revision: 'dd9b242b55ef5371416c53edbd879b5997c719d7'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The backend finds candidate duplicate companies and merges them (stories 4, 5), but organizers have no screen for it (#1251).

**Approach:** A "Possible duplicates" button on the Companies tab opens a page built like `UserManagement/DuplicateCandidatesPage` (pairs with rule, confidence, both sides, "Not a duplicate", "Compare and merge"); the merge opens a `MergeCompanyDialog` with the preview (both sides, moves per table, conflicts, fills, refusal reasons, swap, confirm). A company's detail page offers "Merge with another company" with the same dialog and a company picker.

## Boundaries & Constraints

**Always:** Calls only through a service module in `src/services/api/` and the generated types; all new keys in the 10 locales (de, en first-class, real umlauts); tests through the MSW harness with real i18n, real hooks and services; a touched screen's test moves off own-code mocks (touch rule), ratchet only falls. Organizer only (the Companies tab is organizer-only already).

**Never:** No backend or spec change; no mail; no new route outside `/organizer/companies/*`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| List | pairs from `listCompanyDuplicateCandidates` | one card per pair: rule, confidence, both sides (display name, key, website, users, registrations, partner chip), suggested target as "Keep" | |
| Filter | confidence toggle | only that confidence | |
| Empty | `[]` | "No possible duplicates" | |
| Counts missing | 502 `DUPLICATE_COUNTS_INCOMPLETE` | specific message, retry button | |
| Dismiss | "Not a duplicate" | POST `{nameA: target, nameB: source}`; pair gone | error alert |
| Both partners pair | `organizerCanMerge` false | no merge button, hint | |
| Preview | dialog open | both sides, moves (n > 0) with table labels, conflicts, fills | 502 `MERGE_PREVIEW_INCOMPLETE`: own message |
| Swap | "Swap direction" (when `canSwap`) | preview reloads with target and source exchanged | |
| Refusal | reasons `BOTH_PARTNERS`, `TARGET_KEY_TOO_LONG`, `BLOCKED`, `SAME_COMPANY` | reason text; confirm disabled | |
| Confirm | `canMerge` | POST `{sourceName}` to target; success text; list refreshed | 409/502 by `errorCode` (`MERGE_BLOCKED_BOTH_PARTNERS`, `MERGE_BLOCKED`, `MERGE_NOT_ALLOWED`, `MERGE_INCOMPLETE`) |
| Single company | detail page "Merge with another company", pick company | same dialog, current company as target; after a swapped merge the page goes to the company that stays | |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/UserManagement/DuplicateCandidatesPage.tsx`, `MergeProfileDialog.tsx` -- the model to mirror (layout, filter, dismiss cache update, preview/merge flow). Not changed.
- `web-frontend/src/components/shared/Company/CompanyManagementScreen.tsx` -- header button, route `duplicate-candidates` (a company key is `^[a-zA-Z0-9]{1,12}$`, so a hyphenated path cannot shadow one), `CompanyDetailWrapper` hosts the single-company dialog.
- `web-frontend/src/components/shared/Company/CompanyDetailView.tsx` -- action row (Delete, Edit): add optional `onMerge` button.
- `web-frontend/src/components/organizer/PartnerManagement/CompanyAutocomplete.tsx` -- reuse as picker with `allowCreate={false}`.
- `web-frontend/src/services/api/companyApi.ts` -- existing client; new calls go in a new `companyMergeApi.ts` beside it (axios errors kept raw so `errorCode` is readable).
- `web-frontend/src/types/generated/company-api.types.ts` -- `CompanyDuplicateCandidate`, `CompanyMergePreview`, `CompanyMergeResult`; request body is `sourceName`.
- `web-frontend/src/test/msw/api.ts`, `src/test/auth.tsx`, `src/test/test-utils.tsx` -- harness, `renderWithAuth`, `renderWithProviders`.
- Tests touched: `Company/__tests__/CompanyManagementScreen.test.tsx` (mocks 3 hooks, CompanyList, useAuth), `Company/__tests__/CompanyDetailView.test.tsx` (mocks react-router navigate, dead child mocks, `useUserManagement`): both move to the harness.
- `web-frontend/public/locales/*/common.json` -- `company.duplicates.*`, `company.merge.*` (namespace of the screen).
- Docs: `docs/user-guide/entity-management/companies.md` (new section), `docs/user-guide/appendix/changelog.md` (replace "The organizer screen follows"), `docs/architecture/06b-user-lifecycle-sync.md` §"Merging two companies" (one line: the UI).

## Tasks & Acceptance

**Execution:**
- [x] `src/services/api/companyMergeApi.ts` -- list, dismiss, preview, merge, `apiErrorCode(error)`.
- [x] `src/components/shared/Company/MergeCompanyDialog.tsx` + test -- preview/swap/reasons/confirm/error codes; picker when no preset.
- [x] `src/components/shared/Company/CompanyDuplicatesPage.tsx` + test -- list, filter, dismiss, merge, empty, 502.
- [x] `CompanyManagementScreen.tsx`, `CompanyDetailView.tsx` -- button, route, `onMerge`; their tests rewritten on the harness incl. the new button and route.
- [x] Locales x10, docs, changelog; ratchet baseline updated (STALE lines removed).

**Acceptance Criteria:**
- Given an organizer on the Companies tab, when they click "Possible duplicates", then the pairs page opens at `/organizer/companies/duplicate-candidates`.
- Given a company detail page, when the organizer picks another company in "Merge with another company", then the preview of merging it into the open company shows.
- Given the new keys, when the 10 locale files are compared, then every key exists in each.

## Implementation Notes

- Implemented directly in the session (no implementation subagent: one heavy build at a time on rack, caller's rule).
- `companyMergeApi.ts` keeps axios errors raw; `apiErrorCode` reads `errorCode`. Merge errors map to `company.merge.error.*`, the preview's `MERGE_PREVIEW_INCOMPLETE` and the list's `DUPLICATE_COUNTS_INCOMPLETE` to their own text.
- After a merge the dialog navigates first (swapped merge deletes the open company), then invalidates every `companies`/`company` query except the preview and the deleted key.
- The detail page keeps the dialog by the key it was opened on (`mergeFor`), so its result stays visible after the page moves to the company that stays.
- Touch rule: `CompanyManagementScreen.test.tsx` and `CompanyDetailView.test.tsx` rewritten on the harness (dead mocks of `AssociatedUsersPanel`/`ActivityTimeline` removed); `CompanyDetailView.test.tsx` left the `tsconfig.test-harness.json` exclude list (it type-checks now).
- Store sizes at end: ratchet baseline 528 -> 524 lines; test-harness exclude list 97 -> 96.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens, run by the building session itself: the caller forbade subagents, so it is not a context-free review). high 0, medium 0, low 1, false 1, maybe-false 0.

| Finding | Verdict | Route | Evidence |
|---|---|---|---|
| Detail page: dialog gated on `company && isMergeOpen` could remount and lose the success text when a swapped merge moves to the target page while its company loads | low | patch | Not reproducible today (the picker's `useCompany(other)` keeps the target cached, the new screen test passed before the change), but the gate depended on that cache; replaced by `mergeFor` state, test `should_openTheCompanyThatStaysAndKeepTheResult_when_theOpenCompanyWasMergedIn` |
| "Possible duplicates" button visible to non-organizers | false | reject | `/organizer/*` is organizer-only (`authRules.ts` path rules); a partner only reaches `/organizer/analytics` |

## Design Notes

**Architecture note.** Modules: web-frontend company screens only. New dependencies: none. Endpoints: none new (story 4, 5 endpoints consumed). Reused: `CompanyAutocomplete` (partner management), MSW harness, user duplicates page pattern. Deviations: none.

**Decisions taken (no human available, sensible default):**
1. Route `duplicate-candidates`, not `duplicates`: cannot collide with a company key.
2. New keys in `common` (`company.*`), the namespace all company screens use.
3. Swap offered whenever the preview says `canSwap`, not only on `TARGET_KEY_TOO_LONG`.
4. Picker excludes creating a company (`allowCreate={false}`).
5. After a merge from the detail page that deleted the open company (swapped), navigate to the target.

**Store sizes at start:** ratchet baseline 528 lines (CompanyManagementScreen 2 lines, CompanyDetailView 2 lines). End must be smaller by those 4.

## Verification

**Commands:**
- `cd web-frontend && npx vitest run src/components/shared/Company --maxWorkers=4` -- expected: all pass
- `cd web-frontend && npx vitest run --maxWorkers=4` -- expected: all pass
- `cd web-frontend && npm run type-check && npm run lint` -- expected: clean
- `make check-test-ratchet check-i18n-keys check-frontend-api-paths` -- expected: clean
