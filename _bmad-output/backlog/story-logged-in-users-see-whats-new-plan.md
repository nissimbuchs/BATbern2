---
title: 'Logged-in users see the features added since they last looked'
type: 'feature'
ticket: '2'
created: '2026-11-13'
status: 'in-review'
baseline_revision: 'a1496b5cc68a9e0e71d309ae20d271c79dd1dda6'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: ['{project-root}/_bmad-output/backlog/story-logged-in-users-see-whats-new.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Attendee features built in the last months (session Q&A, topic suggestions, logo requests, profile merge, ...) are hard to find, because the site mostly shows the next event.

**Approach:** A feature list in the frontend code with texts in 10 locales. A dismissible "What's new" card at the top of the attendee dashboard shows the entries released after the user's server-stored "last seen" time, at most 3. A dot on the user menu of both headers, plus a "What's new" menu item, leads there. Dismissing the card stores the time on the user's settings (CUMS).

## Boundaries & Constraints

**Always:**
- Every logged-in user, whatever the role, gets the card, the dot and the menu item. There are no per-role entries (owner 2026-11-13).
- The first list, in this order: session Q&A and comments (two weeks before to two weeks after the event), suggest topics, login and profile, company logo request, thank the organizers, event history, self-nomination. Most important (shown when nothing has been seen yet): Q&A, suggest topics, company logo.
- The profile merge is a conditional entry, ranked first, shown only when the user has same-name claim candidates. It links to the existing offer on the dashboard (owner go 2026-11-13: build this story).
- The stored time lives in the existing self-service settings (`PATCH /users/me/settings`). No new endpoint.
- Texts in all 10 locales. EN and DE are first-class.

**Never:**
- No modal, tour or auto-open. Login, onboarding and the terms gate stay as they are.
- No admin editing of the list and no database table for entries.
- No change to the rest of the dashboard or to the claim flow itself.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| First time | seenAt null | 3 most important entries (merge first if it applies, then the next by importance), dot shown | — |
| Seen before | seenAt set, 1 entry newer | only that entry, dot shown | — |
| Up to date | no entry newer than seenAt, no merge candidate | no card, no dot | — |
| Dismiss | click close | card and dot gone, PATCH sets seenAt=now; another browser shows none | PATCH fails: card stays, no crash, error not shown as a page error |
| Merge applies | candidates non-empty, merge entry newer than seenAt | merge entry first, total ≤ 3 | candidates call fails: entry omitted |
| Settings unreadable | /users/me settings missing | no card | silent |
| Logged out | — | no card, no dot, no menu item | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/pages/attendee/AttendeeDashboardPage.tsx` -- L96-102: h1, then `<EarlierProfilesSection variant="banner" />`. Card goes between h1 and banner. i18n ns `events`, `attendee.dashboard.*`. Landing page only for attendee-only users (`pages/Dashboard.tsx` L34-53); other roles reach it through "My Events".
- `web-frontend/src/components/user/EarlierProfilesSection/EarlierProfilesSection.tsx` -- candidates query `['users','me','profile-claims']`, `listProfileClaimCandidates` (`services/api/userAccountApi.ts` L448). Reuse the same key. Needs `id="earlier-profiles"` as link target (only a testid exists).
- `web-frontend/src/components/public/Navigation/PublicNavigation.tsx` -- avatar button `public-nav-user-menu` L153-162 (Tailwind). Dot on the initials circle; menu item from ~L164.
- `web-frontend/src/components/shared/Navigation/AppHeader.tsx` L233-240 (MUI `Badge` already imported, L192) and `UserMenuDropdown.tsx` L117-185 -- dot and menu item for the portal header.
- `web-frontend/src/services/api/userApi.ts` L38 `updateUserSettings` (PATCH, partial). `hooks/useUserProfile` (`['userProfile']`, includes settings), `contexts/AuthContext.tsx` L125.
- CUMS: `domain/User.java` `@Embedded UserSettings settings` L197 (columns `settings_*`); `controller/UserSettingsController.java` L34/L43; `service/UserSettingsService.java`. Test pattern: `UserSettingsControllerIntegrationTest`.
- `docs/api/users-api.openapi.yml` -- `UserSettings` L3652, `UpdateUserSettingsRequest` L3746.
- Migration: latest here V30; PR #1309 adds V31, so the new one is V32.
- MSW harness: `web-frontend/src/test/msw/` (example `pages/profile/__tests__/ProfilePage.test.tsx`). `AttendeeDashboardPage.test.tsx` is on MSW since story 3 (commit 63ac36449, which also reshaped the dashboard's upcoming section); `EarlierProfilesSection.test.tsx` still `vi.mock`s our services.
- `web-frontend/src/components/auth/ReleaseNotesInfoBox/` -- no import outside its folder (grep 2026-11-13): dead code.

## Tasks & Acceptance

**Execution:**
- [ ] `services/company-user-management-service/src/main/resources/db/migration/V32__add_settings_whats_new_seen_at.sql` -- nullable `TIMESTAMPTZ settings_whats_new_seen_at` on the users table -- stores the last-seen time.
- [ ] CUMS `UserSettings` embeddable, `UpdateUserSettingsRequest`/response mapping, `UserSettingsService` -- add `whatsNewSeenAt`, validate it is not in the future (+5 min tolerance) -- self-service, existing `@PreAuthorize` covers it. Integration test first (PATCH then GET; future value 400; anonymous 401).
- [ ] `docs/api/users-api.openapi.yml` -- add the field to both schemas; `npm run generate:api-types`.
- [ ] `web-frontend/src/features/whatsNew/catalogue.ts` -- entries `{ key, releasedOn, important, link, condition? }`; pure `selectEntries(entries, seenAt, hasClaimCandidates)` implementing the matrix; unit tests for each matrix row.
- [ ] `web-frontend/src/features/whatsNew/useWhatsNew.ts` -- reads settings via the existing profile query and the candidates query, returns entries, `hasNew`, `dismiss()` (PATCH, invalidates `['userProfile']`). Disabled when logged out.
- [ ] `web-frontend/src/features/whatsNew/WhatsNewCard.tsx` -- card with title, sentence, link per entry and a close button; rendered in `AttendeeDashboardPage` with `id="whats-new"`.
- [ ] `PublicNavigation.tsx`, `AppHeader.tsx`, `UserMenuDropdown.tsx` -- dot when `hasNew`; "What's new" item to `/attendee/dashboard#whats-new`.
- [ ] `EarlierProfilesSection.tsx` -- add `id="earlier-profiles"`.
- [ ] `public/locales/{10}/events.json` (card, entries) and nav namespace (menu item) -- all 10 locales.
- [ ] Tests on MSW: new `WhatsNewCard`/dashboard test; move `AttendeeDashboardPage.test.tsx` and `EarlierProfilesSection.test.tsx` off `vi.mock` (touch rule); header dot test.
- [ ] Delete `components/auth/ReleaseNotesInfoBox/` after re-checking no import and no route -- dead code.
- [ ] `docs/user-guide/appendix/changelog.md` and the doc-drift-mapped docs for user settings -- one entry.

**Acceptance Criteria:**
- Given a user whose stored time is older than an entry, when they open any page, then the user menu shows the dot, and the dashboard shows the card with only the newer entries (≤ 3).
- Given the user closes the card, when they reload or log in from another browser, then neither the card nor the dot shows.
- Given an organizer, partner or speaker, when they open their own landing page, then the dot and the menu item are there and lead to the card.
- Given a logged-out visitor, then no card, dot or menu item shows.

## Implementation Notes

Built 2026-11-13 on `feature/whats-new-card` (not pushed, no PR).

- **Backend (CUMS).** `V32__add_settings_whats_new_seen_at.sql` adds nullable `TIMESTAMPTZ settings_whats_new_seen_at` to `user_profiles`. `UserSettings` embeddable gets `Instant whatsNewSeenAt`; `UserSettingsService` maps it both ways; a value ahead of the server clock is stored as the server's now (review finding 3; first built as a 400 beyond +5 min, which kept the card open for a client with a fast clock). No new endpoint; the existing `@PreAuthorize("isAuthenticated()")` on `PATCH /users/me/settings` covers it. The response omits it while null (CUMS serialises non-null only), so the spec has it optional and nullable. Four ITs in `UserSettingsControllerIntegrationTest`: PATCH then GET (also `GET /users/me?include=settings`, column checked by SQL, a later PATCH keeps it), +2 min and +1 day stored as the server's now, anonymous PATCH 401. The red phase was a compile failure (the getter did not exist), not run separately.
- **Spec + types.** `whatsNewSeenAt` (date-time) on `UserSettings` and `UpdateUserSettingsRequest`; `npm run generate:api-types` regenerated `user-api.types.ts`. EMS and PCS generate clients from users-api too; the field is additive and optional.
- **Frontend.** `features/whatsNew/catalogue.ts` (list in owner order, `releasedOn` from the merge dates in git: Epic 7 #776 2026-06-12, SSO/extra e-mails 2026-06-07, merge #1204 2026-11-04, logo request #1247 2026-11-07; `selectEntries` ranks conditional, then important, then list order, max 3), `useWhatsNew.ts` (profile query `['userProfile']` + candidates query, both disabled when logged out; `dismiss()` PATCHes `new Date().toISOString()`, sets the cache, invalidates; on failure logs a warning and the card stays), `WhatsNewCard.tsx` (`id="whats-new"`, Tailwind only), `scrollToFragment.ts`. The candidates query moved into `hooks/useProfileClaimCandidates` so the card and `EarlierProfilesSection` share one key; the section got `id="earlier-profiles"`.
- **Dashboard.** Card between h1 and the merge banner. Invisible link targets added: `id="suggest-topics"` on the topic panel wrapper, `id="attendee-past"` on the past section. A small `useScrollToFragment` scrolls to the URL fragment once its element renders (React Router does not).
- **Headers.** `PublicNavigation`: dot on the initials (desktop and mobile), "What's new" link in the desktop popover and the mobile panel. `AppHeader`: MUI `Badge variant="dot"` on the avatar; `UserMenuDropdown` gets a `whatsNewHasNew` prop and a "What's new" item navigating to `/attendee/dashboard#whats-new`. The menu item is shown to every logged-in user, the dot only when something is new.
- **i18n.** `events.json` → `whatsNew.{title,close,entries.*}` and `common.json` → `menu.whatsNew`, `menu.whatsNewAvailable`, all 10 locales (EN, DE first-class; gsw-BE, rm and the rest are straight translations, a native speaker may polish them).
- **Leave-cleaner.** `components/auth/ReleaseNotesInfoBox/` deleted (no import, no route; re-checked). Its whole pipeline went with it: `web-frontend/public/release-notes.txt`, `scripts/staging/push-release-notes.sh`, its `scripts/README.md` sections, the `releaseNotes` key in 10 `common.json`, and the mention in `docs/plans/public-homepage-prerender.md`. `EarlierProfilesSection.test.tsx`, `PublicNavigation.test.tsx` and `PublicLayout.test.tsx` moved off `vi.mock` of our services/hooks onto the MSW harness / real `AuthContext` (`PublicNavigation.test.tsx` also leaves the test type-check exclude list; `PublicLayout.test.tsx` broke because the navigation now needs a query client). Ratchet start: fe-mock-hooks 99 files/162, fe-mock-services 138/157; end: 96/159, 137/156 (baseline updated, no RAISED line).
- **Tests.** `catalogue.test.ts` (one test per matrix row plus list order, importance and dates), dashboard card tests on MSW (first view, seen before, up to date, close + "other browser" via a fresh query cache against a stateful handler, PATCH failure keeps the card without a page error, merge first, candidates failing drop the merge, settings missing, profile failing), header dot tests in `PublicNavigation.test.tsx` (4 roles, up to date, logged out asks nothing), `AppHeader.test.tsx`, `UserMenuDropdown.test.tsx`, `authRules.whatsNew.test.ts`.
- **Docs.** `docs/user-guide/appendix/changelog.md` (one entry), `docs/architecture/04-api-user-management.md` and `06-backend-architecture.md` (doc-drift mapping of `UserSettingsService`), `docs/user-guide/features/README.md` (mapping of `web-frontend/src/`).
- **Verified.** CUMS `test` 0 failures (935 passed; log `~/.cache/batbern-test-logs/whatsnew-cums-full.log`); EMS/PCS `compileJava` against the changed users-api; vitest full run 464 files, 5898 passed, 0 failed (`whatsnew-vitest-full2.log`); `type-check`, `lint`, `check:api-paths` (275/275), `check-i18n-keys`, `check-test-ratchet` pass.
- **Not done.** The manual walkthrough on local dev (attendee, speaker, organizer, second browser) was not run in this session; the local dev services run old builds and would need the CUMS restart with V32.

## Plan Change Log

- 2026-11-13: `contexts/authRules.ts` gives organizer, speaker and partner `/attendee` path access. Without it, a user without the ATTENDEE role (organizer-only, for instance) clicking "What's new" was redirected by `ProtectedRoute` to `/dashboard`, which breaks the frozen "every logged-in user, whatever the role" and the AC "lead to the card". The EMS endpoint behind the dashboard is already `isAuthenticated()`. Side effect: such users can now open the whole attendee dashboard (their own registrations, topic suggestion), which they could already reach via the API.
- 2026-11-13: Menu item for every logged-in user; with nothing new it leads to the dashboard without a card (the matrix says "no card" when up to date). Owner may prefer to hide the item or show an "up to date" line then.

- 2026-11-13 (review pass 1): a future `whatsNewSeenAt` is stored as the server's now instead of 400 (finding 3).
- 2026-11-13 (review pass 1): What's new reads the settings with `GET /users/me/settings` under its own query key (`['users','me','settings','whatsNew']`) with `skipAuthRedirect`, instead of the shared `['userProfile']` query, which has no per-call opt-out (finding 4). Dismiss sets that key and still invalidates `['userProfile']`. `listProfileClaimCandidates` always sends `skipAuthRedirect`: its one query is shared by the dashboard offer and the card, and the offer renders nothing on failure anyway.
- 2026-11-13 (review pass 1): selection tests run on fixture entries; the live list is checked only for invariants (unique keys, no future date, texts in all 10 locales, the owner's seven in order); the dashboard test derives its expectations from the live list (finding 2). `AppHeader.test.tsx` moved to the real AuthContext, UI store and i18next plus MSW, with `window.matchMedia` for the width (finding 5; fe-mock-hooks 96/159 → 95/156, fe-mock-i18n 96/96 → 95/95). Menu items scroll when already on the dashboard (finding 9); `.prettierignore` line removed (finding 10); `releasedOn` comment (finding 1).

## Review Triage Log

Pass 1 (quick lens, diff 63ac36449..033fec09a). Verdicts: high 0, medium 4, low 3, false 2, maybe-false 0 (+1 not a code finding).

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | Entry released before its deploy is missed by users who closed the card that day | low | patch (comment) | Opening the PR deploys it, so the gap is hours on the release day, not days. Comment in catalogue: `releasedOn` = the day the PR is opened. |
| 2 | Selection tests assert the live list with fixed dates; the next entry breaks them | medium | patch | Selection tests move to fixture entries; live-list tests keep only invariants. |
| 3 | Fast client clock (>5 min): every dismiss 400, card never closes | medium | patch | Server stores min(value, now) instead of refusing. |
| 4 | Profile-claims read on public pages can redirect to /login on 401 | medium | patch | Matrix says "entry omitted"; add `skipAuthRedirect` to the reads used only by What's new. |
| 5 | AppHeader.test.tsx still mocks own hooks (touch rule) | medium | patch | coding-standards Touch rule; move to the MSW harness. |
| 6 | Local-dev walkthrough not run | n/a | step 5 | Done before the PR, not a code defect. |
| 7 | sessionQna/thankOrganizers/selfNomination link to `/` | false | reject | `/` renders HomePage with the current event (Q&A, thanks) and the upcoming cards with self-nomination (App.tsx L758). |
| 8 | V32 without V31 on this branch: out-of-order skip of V31 | medium | defer→handled | Branch is rebased on develop after #1309 merges, before the PR; checked at PR time. |
| 9 | Header "What's new" item does not scroll when the hash is already set | low | patch | Items call `scrollToFragment` when already on the dashboard. |
| 10 | `.prettierignore` still lists deleted release-notes.txt | low | patch | Delete the line. |

## Design Notes

Architecture note:
- Modules: CUMS user settings (`controller`/`service`/`domain`), frontend `features/whatsNew` (new), the two headers, the attendee dashboard.
- New dependencies: none.
- New endpoints: none. The field extends `PATCH /users/me/settings` (`updateUserSettings`), the nearest self-service write, and comes back through `GET /users/me?include=settings`.
- Reused: the profile and claim-candidates queries, MUI `Badge`, the MSW harness.
- Deviations: none.

The list is code, not data: every new feature adds its entry in its own PR, versioned and translated with the feature. `releasedOn` is a date. "Newer" means `releasedOn` > `seenAt`. Dismiss stores the server-validated time "now", so later entries come back.

Leave-cleaner: delete the dead `ReleaseNotesInfoBox`; move the two touched tests to MSW (ratchet must shrink). Record `make check-test-ratchet` counts at start and end.

## Verification

**Commands:**
- `./gradlew :services:company-user-management-service:test --max-workers=4` -- 0 failures
- `cd web-frontend && npx vitest run --maxWorkers=4` -- 0 failures; `npm run type-check`, `npm run lint`, `npm run check:api-paths` pass
- `make check-test-ratchet` -- no RAISED line, count lower than at start

**Manual checks:**
- Local dev as an attendee, a speaker and an organizer: dot, menu item, card, dismiss, reload in a second browser. A user with a same-name candidate sees the merge entry first.
