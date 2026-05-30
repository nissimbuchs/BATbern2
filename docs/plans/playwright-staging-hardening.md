# Plan: Harden Playwright E2E tests to gate staging deploys with auto-rollback

## Context — why this work

The Bruno API-contract suite is now a **blocking** staging-deploy gate with auto-rollback
(`docs/plans/bruno-staging-hardening.md`, complete). Bruno covers the API layer. The
**UI layer is unguarded**: the Playwright suite is fully **disabled** in CI —
`.github/workflows/deploy-staging.yml:1292` carries `if: false` + `continue-on-error: true`
as a *step inside* the big `deploy-to-staging` job (not its own job). It has never reliably
run against deployed staging, so frontend regressions ship unblocked even when Bruno is green.

We mirror the Bruno effort for Playwright: bring all ~411 tests (58 specs across
`chromium`/`speaker`/`partner` projects) to the **same** blocking, auto-rollback gate —
incrementally, **one entity slice at a time** — reusing the gate machinery Bruno already
built. The intended outcome: a UI regression on staging (= production) fails the deploy and
triggers the existing ECS rollback, exactly as a Bruno failure does today.

This is a **reliability + safety + gating** effort, not a coverage-gap fill — every main
entity already has *some* spec. The work is to make those specs trustworthy enough to gate a
production deploy.

### Decisions locked with the product owner (2026-05-30)

| Decision | Choice | Consequence |
|---|---|---|
| Gate target | **Full CRUD against deployed staging (= prod)** | UI flows create real prod rows; every mutating test MUST clean up via the existing `/admin/test-fixtures` endpoints. Highest fidelity; reuses Bruno's cleanup infra. |
| Gate scope | **Curated `@smoke` per deploy; full `@gate` nightly** (OQ-1 resolved 2026-05-30) | Per-deploy gate = a fast curated `@smoke` subset (one mutating+cleanup path per entity, ~30–60 tests) that blocks the deploy + triggers rollback. The full `@gate` suite (everything proven) runs on a nightly schedule. Keeps deploys fast and rollbacks meaningful while still gating everything daily. |
| Per-slice goal | **Reliability-first** | Green & harden existing specs; author net-new ONLY for the empty `test.fixme()` stubs. |
| Test quality bar | **No exceptions** | When a slice is touched: every locator becomes `data-testid`; one shared test-data naming convention; **no empty tests** — every stub is written or deleted. |

## Current status

> **Where we are (2026-05-30):** **PR 1 + full slice 7 MERGED to develop** — squash commit
> `8a9949a1` (GitHub PR #691). The `playwright-tests` job is live in `deploy-staging.yml` but
> **NON-BLOCKING** (onboarding §A2); **Bruno remains the sole authoritative gate**. The
> nightly `@gate` workflow (A9) is live. Once this PR's frontend deploys, the merged
> `@smoke`/`@gate` specs go green (their new testids are now in the build).
>
> **Merged in #691:** PR 1 infra (factory + cleanup + teardown, runner, tagging scheme,
> nightly workflow) + all of slice 7 — the four `archive-*` specs, `registration-flow`
> (slice 7's first **`@smoke`**: a mutating happy-path that creates a throwaway `CREATED`
> event via the API in `e2e/helpers/event-fixture.ts`, registers through the UI, then tears
> down residue-free — event delete → cascade reg, explicit company-slug delete, `bruno.test%`
> user sweep), and `presentation` (read-only `@gate`). Also deleted the dead
> `ArchiveEventDetailPage.tsx` + its unit test.
>
> **Post-merge gate topology (load-bearing):** `playwright-tests` `needs: [deploy-to-staging,
> bruno-tests]` — serialised AFTER Bruno (commit `1f67498e`). This is REQUIRED, not cosmetic:
> Playwright's `global-teardown` shares Bruno's canonical prefixes by design (§A3/§A4), so
> running them concurrently let the Playwright `bruno.test%` sweep delete Bruno's live fixture
> user mid-run → false auto-rollback (the first #691 push failed this way). Keep this ordering
> through the PR 15 gate-flip.
>
> **#691 review follow-ups (carried on `e2e-uploads`, NOT yet merged):** prod-URL `www` fix
> + `eventNumber` collision salt (`aef98a5a`); `COGNITO_CLIENT_ID` dedup to workflow env +
> `@smoke` status in `$GITHUB_STEP_SUMMARY` (`139a09db`). These ride the next PR.
>
> **NEXT:** slices 1 (**uploads**) + 2 (**companies**) MERGED — squash `c46c6463` (PR #692).
> Slice 3 (**users**) MERGED — squash `815b4078` (PR #693). Slices 4 (**topics + event-types**)
> + 5 (**tasks**) MERGED — squash `9ff51818` (PR #694). Slice 6 (**sessions/slot-assignment**)
> MERGED — squash `41669b5b` (PR #695; its deploy-to-staging `@smoke` gate job ran green). Slices
> 8 (**speaker pool**) + 9 (**speaker portal**) are on `e2e-speaker` (rebased onto develop
> post-#695; green ×2 vs dev). NOTE: this repo **auto-merges PRs once CI is green** — so each
> stacked PR auto-merges + deploys when targeted at develop. Per-slice loop = §C "Repeatable
> per-slice checklist". Run locally green ×2 vs dev first
> (`run-playwright-tests.sh development --slice <name>`, §F).

Update this one line on every PR merge so a fresh session can pick up without re-reading the whole plan.

## Progress log

This plan is **not** tracked as BMad stories — it's test-infrastructure work + opportunistic
bugfixes. The table below is the single source of truth.

**PR description convention:** every PR includes `Refs: docs/plans/playwright-staging-hardening.md PR #<n>`
and the final commit updates its row (status, merged date, findings/bugs). Bugs found during a
slice audit land as separate fix-commits in the same PR.

| PR # | Branch | Slice / scope | Specs touched | data-testid gaps filled | Green ×2 | Gate tag | Status | Findings |
|------|--------|---------------|---------------|-------------------------|----------|----------|--------|----------|
| 1 | `e2e-staging-hardening-infra` | A+B infra: `playwright-tests` job (+ edge-readiness poll), `global-teardown`, cleanup helper, test-data factory, runner script (`--scope`), `@smoke`/`@gate`/`@quarantine` scheme, nightly workflow (A9), enable dormant step (non-blocking) | `smoke.spec.ts` (new), `speaker-onbehalf-vs-self-byte-identity` (collection-blocker fix), `api-helpers` (delegate) | — | ✅ staging | `@smoke`+`@gate` (seed) | ✅ merged (#691, `8a9949a1`, 2026-05-30; combined w/ PR 8) | see "PR 1 deviations" below |
| 2 | `e2e-uploads` | Slice 1: file-upload/uploads | `user-account/photo-upload` | `profile-photo-input` | ✅ dev | `@gate` (control) + **`@smoke`** (upload+remove mutating) | ✅ merged (#692, `c46c6463`, 2026-05-30) | **bug found+fixed: self-service photo removal was broken** (`DELETE /users/me/picture` had no handler → fell through to admin `/{username}` with literal `me` → 404). Added `@DeleteMapping("/me/picture")` + integration test + OpenAPI `delete`. Also: presigned-PUT auth-header strip helper (global `extraHTTPHeaders` Authorization broke S3/MinIO uploads). Carries #691 follow-ups (`aef98a5a`, `139a09db`). See "PR 2 notes". |
| 3 | `e2e-uploads` (stacked) | Slice 2: companies | `company-management/{company-creation,company-search}`, `api-integration/companies-api-integration` | — | ✅ dev | `@gate` (×10) + **`@smoke`** (UI create+cleanup) | ✅ merged (#692, `c46c6463`, 2026-05-30) | **fixes 3 prod-residue sources** (`E2E Test Company` no-cleanup POST; `Acme/Beta/Gamma` + cleanup-by-missing-`id`; `TestCompany-…`). Rewrite-to-reality: deleted/consolidated 26 dead·skip·duplicate tests (37→11 active), moved API contract into the api-integration spec, removed stray `.bak`. All data → canonical `BRUNOTESTCO%` + `cleanupById`. See "PR 3 notes". |
| 4 | `e2e-users` | Slice 3: users | `user-management/{user-creation,user-deletion,role-management,user-list-search}`, `user-sync/reconciliation-drift-fix`, `user-account/{profile-management,settings-management}`, `organizer/user-settings-additional-emails` | `user-add-button`, `user-search-input`, `user-clear-filters`, `user-sort-{name,email,company}`, `user-actions-button-<id>`, keyed `user-table-row-<id>`, `user-create-{dialog,close}`, `role-manager-dialog`, `delete-user-{dialog,email,gdpr-warning,cascade-warning}` | ✅ dev (32×2) | `@gate` + **`@smoke`** (UI create+cleanup) | ✅ | **bug found+fixed: stale `window.confirm` handler** in additional-emails spec (component switched to a MUI confirm dialog → row never deleted). **Rewrite-to-reality + prod-safety:** role-management/user-deletion/list-search now operate on a dedicated API-created `bruno.test` fixture user (`e2e/helpers/user-fixture.ts`) instead of the table's FIRST row — the old role spec **saved role changes to a random real prod user**. Deleted 2 user-sync specs (`role-change-sync` dup Bruno `06` + random-user-mutating; `user-registration-sync` all-skipped dup of disabled `09-get-or-create`) + 3 `.backup` files. Dropped authenticated reconcile-POST (mutates prod + not dev-greenable); kept sync-status + auth-negatives. All data → factory; cleanup by captured username. See "PR 4 notes". ✅ merged (#693, `815b4078`, 2026-05-30). |
| 5 | `e2e-topics` | Slice 4: topics + event-types | `organizer/{topic-selection,blob-topic-selector,event-type-selection}` | `EventTypesTab` (`event-types-tab`, `event-type-card-<T>`, `edit-event-type-<T>`, `edit-event-type-modal`), `SlotTemplatePreview` (`slot-template-preview`), `EventTypeConfigurationForm` (`event-type-config-{save,cancel}`), `BlobTopicSelectorPage` (`blob-unsaved-dialog`, `blob-back-{confirm,cancel}`) — **topics needed ZERO** (already richly testid'd) | ✅ dev (18×2) | `@gate` + **`@smoke`** (UI topic create+cleanup) | ✅ merged (#694, `9ff51818`, 2026-05-31) | **rewrite-to-reality + prod-safety.** Topics already fully testid'd → the "zero testids/heavy" estimate was wrong; the testid work was all event-types. **Deleted dead code:** standalone `EventTypeConfigurationAdmin.tsx` + its unit test (the `/organizer/event-types` route now `<Navigate>`-redirects to `/organizer/admin?tab=0`/`EventTypesTab`; the page was unrouted). **Dropped all event-type mutations** (PUT `/events/types` = global prod-config change, no restore; the old PUT-200/400 used unset `E2E_TEST_TOKEN` and the "403 without role" is untestable since playwright.config injects a global `Authorization` header → would mutate, not 403). Topic API-contract describe deleted (dup of Bruno `event-topics-api`). Heat-map + topic→event-selection UI tests deleted (fresh topic has no usage→no heat map; selection's `success-message` never existed + Bruno-covered). Topic cleanup verified: `topic_code` slugifies from title, so `factory.topicCode()` title → swept by `bruno-test-topic-%` + deletable by captured code. See "PR 5 notes". |
| 6 | `e2e-tasks` | Slice 5: tasks | `tasks/test-task-creation-from-templates` (consolidated; `test-task-assignment` **deleted**) | `EventTasksTab` (`event-tasks-tab-content`, `task-template-<id>`, `task-assignee-<id>`) — assignee `organizer-option-<username>` already existed | ✅ dev (2×2) | `@gate` + **`@smoke`** (assign+save+verify, cascade cleanup) | ✅ merged (#694, `9ff51818`, 2026-05-31) | **rewrite-to-reality + prod-residue fix.** Both old specs created a real `BATbern${9000+random}` event via the UI and **never cleaned up** (leaked event+tasks/run) + used role/text locators + HARDCODED organizer names. Consolidated to one spec: read-only `@gate` (Tasks tab lists templates) + **`@smoke`** (assign first default template to the current organizer → save → GET `/events/{code}/tasks` asserts the assignee persisted). Uses the API event-fixture (captured `BATbern{N}`); cleanup = `cleanupByCode` → `event_tasks` FK `ON DELETE CASCADE` (tasks have no prefix-sweep). Deterministic assignee via `organizer-option-<token-username>` (no hardcoded names). `TaskTemplatesTab` (admin template catalog) is a SEPARATE surface, not exercised by these specs. See "PR 6 notes". |
| 7 | `e2e-sessions` | Slice 6: sessions/slot-assignment | `slot-assignment/slot-assignment-workflow` | — (SlotAssignment already strong) | ✅ dev (2×2) | `@gate` + **`@smoke`** (auto-assign) | ✅ merged (#695, `41669b5b`, 2026-05-31; staging `@smoke` gate green) | **rewrite-to-reality.** Replaced an all-skipped 5-test RED-phase `describe.skip` asserting an idealized DOM that was never built (`assignment-progress`/`speaker-card`/`slot-dropzone`/`conflict-detection-modal`+room-change resolution/`speaker-preference-panel`/3-step `bulk-auto-assignment-modal` wizard/`assignment-complete-banner`/`/publishing` walk — NONE of those testids exist). **Zero new testids** — SlotAssignment is already richly testid'd. **Deterministic `@smoke` = auto-assign** (button→`auto-assign-modal`→`auto-assign-confirm`, `POST /sessions/auto-assign`), NOT native HTML5 drag-drop (too flaky to gate). Unassigned-session fixture: REST `POST /sessions` requires timing (`CreateSessionRequest @NotNull`), so `addUnassignedSessions` (event-fixture.ts) creates timed sessions then `DELETE /sessions/timing` to reach the placeholder state. Cleanup = event-delete cascade (`sessions`/`session_timing_history` `ON DELETE CASCADE`) — server-gen `sessionSlug` isn't prefix-sweepable. Verify via `GET /sessions/unassigned == 0`. See "PR 7 notes". |
| 8 | `e2e-registrations` | Slice 7 (**full**): archive sub-slice + `registration-flow` + `presentation` | `archive-{browsing,filtering,event-detail,infinite-scroll}`, `registration-flow`, `presentation` | archive: ArchivePage, EventCard, FilterSidebar, FilterSheet, HomePage(archive), HeroSection, SessionCards, SpeakerGrid, SpeakerDisplay; reg: PersonalDetailsStep (5 inputs+5 errors), CompanyAutocomplete, ConfirmRegistrationStep (terms), RegistrationWizard (success); pres: WelcomeSlide | ✅ local | `@gate` (archive+pres read-only) + **`@smoke`** (registration mutating happy-path) | ✅ merged (#691, `8a9949a1`, 2026-05-30) | full rewrite-to-reality; reg `@smoke` is slice 7's first mutating gate path; see "PR 8 notes". Review follow-ups (#1/#2/#4/#8) carried on `e2e-uploads`, not in this merge. |
| 9 | `e2e-speaker` (stacked on `e2e-sessions`) | Slice 8: speaker pool (organizer) | `organizer/speaker-{column-triage,card-primary-action}` (migrated), `speaker-pool-smoke` (new), DELETED `speaker-{brainstorming,outreach,invitation,kanban-guided-drag}`, untagged `speaker-onbehalf-vs-self-byte-identity` | `MarkContactedModal` (`contact-method-option-{email,phone,in-person}`) — kanban/drawer testids already existed | ✅ dev (5×2) | `@gate` + **`@smoke`** (log-outreach IDENTIFIED→CONTACTED) | 🔵 | **rewrite-to-reality + reliability.** The recon's "strong testids / light" was optimistic: 3 specs were FICTIONAL (`speaker-brainstorming` asserted a non-existent `/brainstorm` route; `speaker-outreach` a non-existent `/outreach` dashboard; `speaker-invitation` mixed) and **all** specs seeded via the unset `process.env.E2E_TEST_TOKEN` (`Bearer undefined`→401) + created events through the UI with `Date.now()` titles and **no cleanup** (event-leak/run). New shared `e2e/helpers/speaker-pool-fixture.ts` (seed/status/promote/get via `readOrganizerToken()`). Kept+migrated `column-triage` (2 tests) + `card-primary-action` (2 tests: IDENTIFIED→MarkContactedModal, CONTACTED→drawer promote sub-view — fixed: the old `promote-email-field` assertion targeted the now-dead legacy `PromoteSpeakerDialog`; post-Epic-11 CONTACTED opens the drawer's `promote-submit-button`). **`@smoke` = log-outreach (IDENTIFIED→CONTACTED via MarkContactedModal)** — deliberately NOT promote-to-READY: promote provisions a Cognito/CUMS user out-of-band, an **intermittently-flaky** external write (observed 500s on dev) that would make a blocking gate spurious; also not native DnD. DELETED `kanban-guided-drag` (manual-mouse DnD, flaky). Cleanup = event-delete cascade (`speaker_pool.event_id` ON DELETE CASCADE). **Follow-up (untagged):** `speaker-onbehalf-vs-self-byte-identity` — its inline event-create 400s (NotNull gap) + walks promote; migrate to `createRegistrationEvent` + a promote-free path to gate it. See "PR 9 notes". |
| 10 | `e2e-speaker` (stacked, with slice 8) | Slice 9: speaker portal | `speaker/{speaker-portal-dashboard,magic-link-teardown,speaker-magic-login-404}` (kept→`@gate`); DELETED 3 fixme stubs (`speaker-portal-{respond,content-submit,cross-portal-nav}`) | `SpeakerDashboardPage` (`speaker-dashboard` root) | ✅ dev (4×2, speaker project) | `@gate` only (no safe deterministic `@smoke`) | 🔵 | **rewrite-to-reality + reliability.** Kept the 3 sound specs and tagged them `@gate`: dashboard renders (switched `getByRole('heading')` → new `speaker-dashboard` testid), `magic-link-teardown` (asserts no `speaker_jwt` cookie / no magic-login calls — Epic 11.F.1), `speaker-magic-login-404` (deprecated route → 404, not the old page). **DELETED all 3 fixme stubs** (no real assertions): `respond` would need an INVITED pool row provisioned via promote-to-READY (a flaky out-of-band Cognito write — same reason slice 8's `@smoke` is promote-free), `content-submit` needs an assigned-session fixture not available, `cross-portal-nav` needs a dual-role test user not provisioned. **No `@smoke`:** there is no safe + deterministic speaker-side mutation (every mutating speaker flow depends on an organizer-provisioned INVITED/assigned state via the flaky promote path); slice 9 gates read-only, like slice 7's archive. Runs under the `speaker` project (`SPEAKER_AUTH_TOKEN`; skips gracefully without it). See "PR 9 notes". |
| 11 | `e2e-event-workflow` | Slice 10: full workflow create→archive | `event-lifecycle-e2e`, workflow walk | — | — | — | ⬜ | **heavy** — see Section C-bis |
| 12 | `e2e-partners` | Slice 11: partners + meetings | `partner-management/*`, `organizer/partner-meetings`, `partner/*` | — | — | — | ⬜ | **no cleanup today** (residue source); needs `PARTNER_AUTH_TOKEN` |
| 13 | `e2e-admin-tabs` | Slice 12: organizer admin tabs | `organizer/admin-settings` | 8 admin tabs, `OrganizerAnalyticsPage`, `NotificationsPage` | — | — | ⬜ | **heaviest** — zero testids |
| 14 | `e2e-cross-cutting` | Slice 13: a11y/auth/cors sweep | `accessibility/*`, `auth/*`, `api-integration/cors-validation` | — | — | — | ⬜ | mostly non-mutating |
| 15 | `e2e-gate-flip` | E+F: flip to blocking, deliberate-fail drill | — | — | — | — | ⬜ | owed: rollback drill |

**Status legend:** ⬜ todo · 🟡 in progress · 🔵 in review · 🟢 @gate (proven ×2) · 🟠 @quarantine · ✅ merged · 🔴 blocked

### PR 8 notes — archive sub-slice (rewrite-to-reality, dead tests removed)

The archive specs were speculative (Story BAT-109): they asserted an idealized DOM and
features that were never built. PO decisions (2026-05-30): rewrite all four against the real
implementation; **delete + log** dead tests. data-testid added to 9 components; all four
specs are testid-only. Tagged `@gate` (read-only — no mutation, so no `@smoke`; the
per-deploy archive gate is already `e2e/smoke.spec.ts`). Green ×2 locally (20/20).

**Key finding — dead production code (RESOLVED):** `/archive/:eventCode` routes to
`<HomePage />` in ARCHIVE mode. The standalone `ArchiveEventDetailPage.tsx` was **not routed
in production** (only its own unit test mounted it). `archive-event-detail.spec.ts` was
rewritten against HomePage's real archive rendering, and the dead `ArchiveEventDetailPage.tsx`
+ its unit test were **deleted** (2026-05-30, this PR) after confirming zero production
references.

**Tests deleted (feature/page never existed):**
- Time-period filter ("Last 5 Years", "2020-2024"): `ArchiveFilters` is `{ topics, search }`
  only — no time-period control or URL param. (Backlog: was a time-period filter ever scoped?)
- "Most Sessions" sort option: `sort-select` offers newest / oldest / most-attended only.
- `active-filter-chip`, `aria-pressed` toggles: never rendered (toggle state is class-based;
  view mode now asserted via `data-view-mode` on the container).
- "empty archive state": unreachable on a populated prod archive via the public UI.
- Detail: `event-header`/`event-topic`/`venue-info`/`registration-deadline`/`sessions-pagination`
  (matched the dead ArchiveEventDetailPage), and **"back preserves filters"** — the event-card
  link is `/archive/<code>` with no query string, so filters are dropped on drill-in and
  cannot be restored on back. *(Backlog: should card links carry the archive filter query?)*
- Infinite scroll: "scroll-position on browser back", "<1000ms perf target", "rapid scroll
  dedup" — all non-deterministic timing; not gate-worthy.

**Quarantined (real assertions, racy):** infinite-scroll "loading indicator" (`@quarantine`)
— the brief fetch state is hard to catch deterministically; promote once a stable wait exists.

**Corrections baked in:** search param is `q` (not `search`); view-mode localStorage key is
`archive-view-mode` (not `archiveViewMode`); sort is a native `<select>`; clear button testid
is `clear-filters`. The `archive-empty-state` / `session-materials` / `material-download`
testids were added for future seeded/mocked tests even though no live test exercises them yet.

### PR 8 notes — `registration-flow` (rewrite-to-reality + slice 7's first `@smoke`)

The prior spec asserted a STALE flow. Reality (verified in `RegistrationWizard.tsx`):
- The wizard is **2-step** (Personal Details → Confirm) — there is no "session selection"
  step. The old spec's 3rd step + extra Next click were fictional.
- Submit is **double-opt-in**: it shows an INLINE "✉️ email sent — click the link to confirm
  (valid 48h)" success view and stays on `/register/:eventCode`. The QR code only exists
  AFTER the emailed confirmation link is clicked, which an E2E run cannot do.

**Tests deleted (asserted an unreachable / never-built flow):**
- "full journey" QR assertions: `toHaveURL(/registration-confirmation|confirm/)`, the success
  text regex, and the QR-code locator — submit never navigates there; the QR is
  post-email-confirmation. Replaced by an assertion on the real inline success view.
- "loading state during submission" — flaky-by-design (races the submit spinner) and would
  leave an un-cleanable registration; not gate-worthy.
- "Calendar Export" describe — `.ics` export lives on the post-confirmation page, unreachable
  without clicking the email link. *(Backlog: cover via a seeded confirmed-registration
  fixture if calendar export ever needs gating.)*

**Mutating `@smoke` + cleanup (the prod-safety crux).** A public registration creates a real
`registrations` row PLUS, out-of-band, a CUMS `user_profiles` + `companies` row (getOrCreate,
cognitoSync=false) — and registrations have **no prefix-sweep path** (EMS cleanup enum is
`EVENTS|SESSIONS|TOPICS` only). So we never register against the live "current" event
(un-deletable). Each run creates a throwaway `CREATED` event via the API
(`e2e/helpers/event-fixture.ts`; the `Event` entity enforces more NotNull fields than
`CreateEventRequest`'s DTO — registrationDeadline, venue*, organizerUsername — all supplied),
registers against IT, asserts the inline success view, then tears down in `afterAll`:
1. DELETE the event → FK cascade removes the registration row.
2. Explicit-DELETE the CUMS company by slug — its lowercased slug (`getOrCreateCompany`
   slugifies) is NOT reachable by the uppercase, case-sensitive `BRUNOTESTCO%` sweep (the same
   gap that leaks Bruno's `testco`/`test.attendee`), so it must be deleted directly.
3. The `bruno.test*` user (first/last `bruno`/`test`) IS reached by the global-teardown
   `bruno.test%` sweep — no explicit delete needed.
Verified residue-free on dev: post-run sweep shows `companies:0 events:0`, user swept.
`test.describe.configure({ mode: 'serial' })` so the shared fixture event is created/torn-down
once (no parallel-worker race on the shared company-slug delete). Forced-anonymous
storageState (the chromium project is authenticated; logged-in users get a one-click panel,
not the public 2-step funnel). Slice 7 is otherwise read-only, so this is its only `@smoke`.

### PR 8 notes — `presentation` (testid hardening, read-only `@gate`)

Replaced the brittle `text=BATbern` "loaded" match with a `presentation-welcome-slide` testid
on `WelcomeSlide`. The deck has TWO `agenda-flip-container` elements — `data-layout="center"`
(agenda-preview / recap) and `data-layout="sidebar"` (session slides) — conditionally mounted,
and during the FLIP both briefly unmount. A naive "press N times then assert" races that
transient and overshoots past the session slides. The hardened nav anchors on the stable
centered agenda-preview slide, steps once into the first session slide, and asserts via a
layout-scoped locator (`[data-testid=agenda-flip-container][data-layout=…]`) that auto-retries
through the FLIP. Read-only/public → `@gate` only (no `@smoke`). Forced-anonymous so AC #1
("loads without authentication") is genuinely exercised. Uses BATbern57 (real archived event,
mirrored locally with 8 sessions).

### PR 2 notes — uploads slice (rewrite-to-reality + a real prod bug)

Slice 1 (`user-account/photo-upload.spec.ts`) had 1 thin active test + 6 `test.skip` stubs
asserting a crop dialog / per-phase progress UI / standalone size-validation screen that were
**never built** (the control is a plain hidden `<input type=file accept=image/jpeg,image/png>`;
upload is the ADR-002 3-phase presigned-S3 flow with no progress UI; failure surfaces only via
`console.error`, no inline error UI). Per the quality bar (#3 no empty tests) all 7 were
**deleted**, replaced by two real tests:
- `should_exposeUploadControl_when_accountLoaded` (`@gate`, read-only) — upload button visible
  + the file input's `accept` attribute is the file-TYPE-validation gate. New testid
  `profile-photo-input` on the input (the spec's only non-testid locator, ProfileHeader.tsx).
- `should_uploadAndRemovePhoto_when_validImageSelected` (**`@smoke`** + `@gate`) — the slice's
  mutating happy path: select a real PNG (`factory.uploadPngFile()`, a valid 1×1 PNG buffer)
  → the 3-phase upload runs → `remove-photo-button` appears (renders only when
  `profilePictureUrl` is truthy = a precise signal, no fixed sleeps) → remove it (AC13). It
  mutates the authenticated organizer test account's OWN photo (lowest blast radius);
  teardown is an explicit `DELETE /users/me/picture` (`cleanupOwnProfilePicture`, no
  prefix-sweep path for uploads) in `afterAll`, leaving the account photo-less. Serial mode
  (single shared account). Auth is via storageState (upload NEEDS the JWT) — no anonymous
  override (unlike registration).

**Bug #1 — self-service photo removal was broken in production (RESOLVED).** The "Remove Photo"
button calls `DELETE /users/me/picture`, but the controller had **no `/me/picture` DELETE
handler** — only the admin `@DeleteMapping("/{username}/picture")`. Spring routed the request
with literal `username="me"` → `UserNotFoundException` → **404 for every user removing their
own photo**, silently swallowed by the UI's `console.error`. Latent because no test covered it
and the button only appears when a photo exists. Fix: added `@DeleteMapping("/me/picture")`
(resolves `me` from the security context like `/me/picture/{presigned-url,confirm}`; the literal
mapping takes precedence over the template) + an integration test
(`should_removeOwnProfilePicture_when_deleteMePicture`) + the missing `delete` operation in
`users-api.openapi.yml` (regenerated types). The OpenAPI spec already had the `POST`
`/users/me/picture` but no `delete` — the contract was missing it too.

**Bug #2 — global Authorization header breaks every presigned-S3 upload (RESOLVED, reusable).**
`playwright.config.ts` injects `Authorization: Bearer <JWT>` via `extraHTTPHeaders` when
`AUTH_TOKEN` is set. Harmless on same-origin API calls (axios sets its own) but FATAL on a
presigned object-store PUT: the store sees BOTH the query SigV4 auth and the header → rejects.
Verified: dev MinIO → 400 `<Code>InvalidRequest</Code> "request has multiple authentication
types"`; curl with no header → 200. AWS S3 rejects the same way, so this would break the
`@smoke` on staging too. Fix: `e2e/helpers/strip-presigned-auth.ts` — a scoped `page.route`
matching the `X-Amz-Signature` query marker that drops `Authorization` from the storage
request only (zero blast radius on the other ~410 specs; the global config is untouched).
**Reuse this in every future upload slice** (company logo, speaker materials, import).

**Local-env caveat (why dev, not staging, for green ×2):** dev uses MinIO (`localhost:8450`),
not AWS — so the full S3 round-trip is exercisable locally once Bug #2 is fixed. Green ×2 on
dev (2/2 both runs). Staging can't validate the `@smoke` until this PR's frontend deploys
(the new `profile-photo-input` testid isn't in the deployed build yet) — the same deploy-then-
green pattern the status note describes. The control test will go green on staging post-deploy.

### PR 3 notes — companies slice (rewrite-to-reality, 3 residue sources killed)

Slice 2 had 37 tests across 3 specs, mostly **API-contract tests written in Playwright**
that duplicate Bruno's companies collection, plus speculative UI tests against an idealized
DOM. Three of them were live **prod-residue sources** (the reason the plan flagged this slice):

1. `companies-api-integration.spec.ts` "POST with all headers" created `E2E Test Company
   ${Date.now()}` — a non-canonical name (not swept by `BRUNOTESTCO%`) — and **never deleted
   it**. One leaked company per run.
2. `company-search.spec.ts` — four describe groups (search/caching/perf/advanced) each
   created `Acme Corporation`/`Beta Technologies`/`Gamma Innovations <ts>` in `beforeAll` and
   "cleaned up" via `deleteCompanyViaAPI(token, company.id)` — but Story 1.16.2 keys companies
   by **name** and the create response has no `id`, so the delete hit `/companies/undefined`
   and every run leaked three companies.
3. `company-creation.spec.ts` — inline `TestCompany-${Date.now()}` / `Test Company Display`.

**Outcome (37 → 11 active tests):**
- `company-creation.spec.ts` → **UI-only**: `should_displayCompanyCreationForm` (`@gate`) +
  `should_createCompany` (**`@smoke`** — the slice's mutating happy path). **Key reality
  find:** the old `should_createCompany` was skipped "dialog not closing" because the name
  field enforces `^[A-Za-z0-9]+$` and `TestCompany-<ts>` has a hyphen → validation blocked
  submit. `factory.companyName()` (`BRUNOTESTCO<ts>`, pure alphanumeric) passes AND is swept.
  Success signal = the dialog closing (CompanyForm calls `onClose()` only after the create
  mutation resolves; a failure renders an apiError Alert and keeps it open). Teardown =
  `cleanupById(token,'companies',name)` in afterEach + the `BRUNOTESTCO%` sweep backstop.
  Deleted the 4 `/companies/create`-route skips (route never existed — it's a modal), the
  duplicate "API Endpoints" group (now in the api-integration spec), and the EventBridge /
  latency skips (non-E2E concerns).
- `company-search.spec.ts` → reduced to the one real read-only assertion (search control +
  list render, `@gate`). Deleted the 5 speculative UI skips (placeholder/autocomplete-results
  testids the real CompanyFilters/CompanyList don't render) and all 4 residue-creating API
  groups (contract covered by the api-integration spec + Bruno).
- `companies-api-integration.spec.ts` → kept the public/auth contract + CORS checks (read-only
  `@gate`); the POST now uses `factory.companyName()` + explicit afterEach `cleanupById`.
- Removed a stray (untracked) `company-search.spec.ts.bak` from the working tree.

`@smoke` is the UI create (one canonical mutating+cleanup path); everything else is `@gate`.
Green ×2 on dev; final sweep shows `companies:0` (residue-free). Note: the `company-search-input`
testid sits on the MUI FormControl wrapper, not the `<input>` — a future search-interaction
test must target `.locator('input')` (noted so the next author doesn't trip on it).

### PR 4 notes — users slice (rewrite-to-reality, a prod-mutation footgun killed)

Slice 3 had 10 specs. The headline risk (why the plan rated it "medium"): the old
`role-management.spec.ts` opened the role modal on the table's **FIRST row** and **SAVED role
changes to it** — i.e. on staging (= prod) it mutated the roles of whatever real user sorted
first, possibly the organizer themselves. `user-deletion`/`user-list-search` similarly leaned
on `tbody tr first` + `text=` locators against unknown real rows.

**New helper — `e2e/helpers/user-fixture.ts`.** `createTestUser(token, roles)` API-creates a
`bruno.test`(.N) user (firstName/lastName = factory `Bruno`/`Test` → server-derived username
the `cums/users` sweep reaches; unique `factory.email()`), returning `{username, email}`.
`findUsernameByEmail(token, email)` resolves the username the create-FORM @smoke can't know
up-front. Every mutating user spec now operates **only** on its own fixture user and tears
down by the **captured exact username** (`cleanupById(token,'users',username)`) — race-free
even under local `fullyParallel` (a broad `bruno.test%` afterAll sweep would delete a sibling
spec's in-flight user; CI is `workers:1` but local isn't). Bruno's `04-create-user.bru` proves
this exact pattern + that the `cums/users` cleanup endpoint removes the **Cognito** user too,
so no Cognito residue leaks.

**`@smoke` = UI user creation** (`user-creation.spec.ts` `should_createUser`): the one
mutating+cleanup happy path. Dialog-close = success signal (a failed create keeps the modal
open with a server-error Alert), mirroring the company-creation @smoke. afterEach resolves the
username via search → `cleanupById`. Everything else is `@gate`.

**Tests rewritten / deleted:**
- `role-management` → operates on the fixture user (search-by-email → its keyed row/actions),
  toggles SPEAKER, saves (dialog-close signal). The old assert-against-first-row + the
  `should_showError_when_deselectingAll` `test.skip` are gone.
- `user-deletion` → API-creates the fixture user per test, drives the real UI delete (GDPR +
  cascade warning testids, confirm → keyed row unmounts). Self-cleaning + `cleanupById`
  backstop. Old form-fill `beforeEach` (create-form path is `user-creation`'s job) removed.
- `user-list-search` → seeds a fixture user as a deterministic search target; deleted the two
  assertion-free tests (`should_sortTable`/`should_displayPagination` logged but asserted
  nothing) and the flaky role-filter Autocomplete test (role filtering is Bruno-covered at the
  API, `15/20-list-users-filter-by-role`).
- **`user-sync/role-change-sync.spec.ts` DELETED** — API role GET/PUT duplicated Bruno
  `06-update-user-roles.bru` AND mutated a random real user; the UI `role-management` gate +
  Bruno cover this.
- **`user-sync/user-registration-sync.spec.ts` DELETED** — all `describe.skip` (get-or-create
  is blocked through the gateway locally), a dup of the **disabled** Bruno `09-get-or-create`;
  the real get-or-create path is exercised by the slice-7 registration `@smoke`.
- `user-sync/reconciliation-drift-fix.spec.ts` **SURVIVES** (unique — `reconcile`/`sync-status`
  are NOT in Bruno). Hardened to `readOrganizerToken` + contract assertions (dropped the
  non-E2E perf/latency timing). **Dropped the authenticated reconcile-POST**: it MUTATES prod
  (the scheduled job already runs it) and is not dev-greenable — against local dev (CUMS DB = a
  partial mirror of staging Cognito) reconcile 500s on a Cognito-only user
  (`User with ID 'bruno.test.N' not found`). Kept the read-only sync-status contract + both
  endpoints' auth-protection negatives.
- `user-account/profile-management` → getByTestId; deleted 5 `test.skip` (verified-badge,
  role-tabs, activity ×2, bio-length); robustified role-badge to "≥1" (was "exactly 2 =
  Organizer+Speaker"). The one mutating test (save bio) captures the org's own bio via the API
  in `beforeAll` and RESTORES it in `afterAll` (own-account capture/restore, like photo-upload).
  Reality: the bio counter is `/5000` (not `/2000`).
- `user-account/settings-management` → getByTestId; deleted 4 persist `test.skip`. Reality:
  `change-password-button` has NO in-app handler (Cognito-managed) → assert presence, not the
  old flaky `dialog-or-urlChanged` click-through; `privacy-policy-link` is an
  `<a target=_blank href=/privacy-policy>` → assert the href/target attributes (the old
  `waitForEvent('popup')` raced a real new-tab load).
- `organizer/user-settings-additional-emails` → factory `email()` (swept by
  `additional_emails`); **bug fixed**: the component now uses a **MUI** confirm dialog (not
  `window.confirm`), so the spec's `page.once('dialog', …)` accept never fired and the row was
  never deleted → now clicks `additional-email-delete-confirm`. Also dropped the dead
  `.playwright-auth-chromium.json` storageState override (ENOENT — the chromium project's
  `.playwright-auth-state.json` is inherited).

**Component testids added** (same commit as the specs): `user-add-button` (UserList),
`user-search-input` (on the native input via `inputProps`) + `user-clear-filters` (UserFilters),
`user-sort-{name,email,company}` + per-row `user-actions-button-<id>` + keyed
`user-table-row-<id>` (UserTable, replacing the generic `user-table-row`), `user-create-dialog`
+ `user-create-close` (UserCreateEditModal), `role-manager-dialog` (RoleManagerModal),
`delete-user-{dialog,email,gdpr-warning,cascade-warning}` (DeleteUserDialog). Factory gained
`USER_FIRST_NAME`/`USER_LAST_NAME` (`Bruno`/`Test`).

Green ×2 on dev (32/32 both runs); final sweep `user_profiles:0 user_additional_emails:0
companies:0` (residue-free). 108 UserManagement unit tests still green after the testid
changes; type-check + lint clean. Staging can't validate the new testids until this PR's
frontend deploys (same deploy-then-green pattern as PRs 2/8).

### PR 5 notes — topics + event-types slice (rewrite-to-reality, dead code removed, mutations dropped)

Slice 4 was rated "heavy (zero testids)". **That estimate was wrong for topics** — the topic
backlog UI already carries rich testids (`topic-backlog-manager`, `new-topic-button`,
`create-topic-modal` + all form fields, `topic-list`, `topic-card-<code>`,
`staleness-score-<code>`, `view-mode-{list,heatmap}`, `filter-category`), so topics needed
**zero** new testids. All the testid work was event-types.

**Topic cleanup correctness (the crux, verified in EMS).** `topic_code` is **slugified from
the title** (`Topic.generateTopicCode`: lowercase, strip to `[a-z0-9 -]`, spaces→`-`). So a
title of `factory.topicCode()` (`bruno-test-topic-<ts>`, already a valid slug) produces
`topic_code === that string` — reachable by the `ems/topics` prefix sweep
(`topic_code LIKE 'bruno-test-topic-%'`, which also bypasses the usage-history delete guard)
AND known up-front, so the `@smoke` deletes by the exact captured code (`cleanupById` → 204 on
an unassigned topic). Confirmed residue-free on dev (final sweep `topics:0`).

**`topic-selection.spec.ts` → 3 tests** (was a large UI + API-contract file): backlog renders
(`@gate`), create-modal opens+cancels (`@gate`), **create topic (`@smoke`+`@gate`)** — the
slice's one mutating+cleanup happy path. Modal-close = success signal (a failed create keeps
the modal open with a `topic-form-error` Alert), exactly like the company/user `@smoke`; no
card-in-list assertion (a new topic's position on a populated, paginated, sort-ordered prod
list is flaky for no extra signal). **Deleted:**
- The entire **"Topics API Contract Tests"** describe — a Playwright re-implementation of
  Bruno's `event-topics-api` collection (`30-list`/`31-create`/`32-get`/`33-select`/
  `34-verify-workflow`/`99a-posttest-cleanup`, same `bruno-test-topic-` prefix). Two of them
  sent `Bearer ${E2E_TEST_TOKEN}` (an env var nothing sets → `Bearer undefined`) → never
  passed. Bruno owns the topic API contract.
- **Heat-map tests** (usage heat map / quarterly frequency / hover tooltip): the detail
  `TopicHeatMap` renders only when `usageHistory.length > 0`; a freshly-created topic has none,
  so they asserted DOM that can't exist without a full event-assignment workflow. Cells also
  have no testids. *(Backlog: cover via a seeded used-topic fixture if heat-map needs gating.)*
- **Topic→event selection / workflow-transition tests**: the asserted `success-message` does
  NOT exist (selection swaps the panel to speaker brainstorming), the flow mutates a real
  event's workflow state, and Bruno `33`/`34` already cover it at the API.
- The category-filter interaction test (filter MenuItems carry no testids; API category
  filtering is Bruno-covered).

**`event-type-selection.spec.ts` → read-only `@gate` only** (no `@smoke` — see below). UI:
selector visible in the New-Event modal; selector shows 3 options; loading-state via route
delay; admin cards render via `event-types-button` → `/organizer/admin?tab=0`; edit modal
opens **and is CANCELLED** (never saved). API: GET `/events/types` (3 types, structural
invariants) + GET invalid → 404. **Reality corrections + deletions:**
- New-Event button is `new-event-button` (NOT `quick-action-new-event`); the modal is
  `create-event-modal`.
- **No `SlotTemplatePreview` in the event form** — the selector shows slot metadata inline per
  option; SlotTemplatePreview lives only in the EventTypesTab admin cards. The old "slot
  preview on select" test asserted non-existent modal DOM → deleted.
- **Dead code deleted:** the standalone `EventTypeConfigurationAdmin.tsx` page + its unit test.
  `/organizer/event-types` now `<Navigate>`-redirects to `/organizer/admin?tab=0` (EventTypesTab
  is canonical); the standalone page was unrouted (only its own test mounted it) — same
  dead-page pattern as PR 8's `ArchiveEventDetailPage`. The old spec's H1 "Event Type
  Configuration" / "ADMIN ONLY" / "Back to Dashboard" assertions matched that dead page → gone
  (EventTypesTab has no H1, no admin-only text, breadcrumbs not a back button).
- **All event-type mutations dropped (prod-safety, plan risk #1):** event-type config is a
  GLOBAL singleton governing every future event of its type. The UI edit flow is opened and
  cancelled. The old `PUT /events/types/{type}` "update" + "400 invalid" tests used the unset
  `E2E_TEST_TOKEN` (never passed) and would mutate global config. The "403 without role" PUT is
  **untestable here**: `playwright.config` injects a global `Authorization` header when
  `AUTH_TOKEN` is set, so a header-less `request.put` still runs AS ORGANIZER → it would 200
  and MUTATE prod config, not 403. All three deleted (same reasoning PR 4 used to drop the
  authenticated reconcile-POST). Slice 4's single `@smoke` is therefore the topic create.

**`blob-topic-selector.spec.ts` → hardened to testid-only.** The unsaved-changes dialog's
confirm/cancel buttons had no testids (the spec used
`getByRole('dialog').getByRole('button',{name:/go back|confirm/i})`) → added
`blob-unsaved-dialog` + `blob-back-{confirm,cancel}`. The old `fit-all`/`snap` test was
**conditional** (`if (isLoaded) …` → asserted nothing when the canvas hadn't loaded) → rewritten
to wait for `blob-canvas` then assert both controls. **Dev-timing find:** `blob-canvas` lives
inside the heavy lazy D3 `BlobTopicSelector` chunk that the Vite **dev** server compiles
on-demand on first navigation (>5s cold; the page shell + back button render first). The
`topic-session-data` fetch itself is ~0.5s. Bumped the canvas-visible timeout to 30s to absorb
the dev first-compile; on staging the bundle is prebuilt so it resolves immediately. Uses
BATbern57 (real archived event mirrored locally, same fixture as the presentation `@gate`).

**Component testids added** (same commit as the specs): `event-types-tab`,
`event-type-card-{TYPE}`, `edit-event-type-{TYPE}` (EventTypesTab) + `edit-event-type-modal` on
its Dialog; `slot-template-preview` (SlotTemplatePreview root); `event-type-config-{save,cancel}`
(EventTypeConfigurationForm); `blob-unsaved-dialog` + `blob-back-{confirm,cancel}`
(BlobTopicSelectorPage). 23 touched-component unit tests still green; type-check + lint clean.
Green ×2 on dev (18/18 both runs); residue-free. `@smoke`→1 (topic create), `@gate`→18 via tag
routing. Staging can't validate the new event-types testids until this PR's frontend deploys
(same deploy-then-green pattern as PRs 2/4/8).

### PR 6 notes — tasks slice (rewrite-to-reality, a no-cleanup prod-residue source killed)

Slice 5 had two specs (`test-task-creation-from-templates` + `test-task-assignment`), both of
which **created a real event through the UI** (`EventWorkflowPage.fillEventForm` →
`BATbern${9000+random}`, an `E2E Test - …` title — neither swept by any prefix) and **never
deleted it** → a leaked event plus its tasks on every run (exactly the residue class the plan
exists to kill). They drove the Tasks tab with role/text locators
(`input[type=checkbox]`, `getByRole('combobox')`, `getByRole('listitem')`,
`getByRole('option',{name:'Nissim Buchs'})`) and **hardcoded organizer display names**
("Nissim Buchs" / "Daniel Kühni" / "Andreas Grütter") — environment-specific and fragile — with
pervasive `waitForTimeout`.

**Consolidated to ONE spec** (`test-task-creation-from-templates.spec.ts`); deleted
`test-task-assignment.spec.ts` (its three-named-assignee flow is fully + deterministically
covered by the `@smoke`). Two tests:
- **`@gate`** (read-only): open the fixture event's edit modal → Tasks tab → assert the
  template rows + per-row assignee selects render.
- **`@smoke`** (mutating + cleanup): assign the first (pre-selected) default template to the
  **current** organizer (`organizer-option-<token-username>`, no hardcoded name) → save →
  dialog closes → **GET `/events/{code}/tasks` asserts a task carries that assignee**. This
  verifies the real AC (task instantiation from templates WITH assignee), stronger than
  dialog-close alone.

**No UI event-create needed (reliability).** Reality (EventForm.tsx:205-250): editing an event
with NO existing tasks pre-selects all default templates (enabled, not disabled), so an
API-created throwaway event shows assignable rows immediately. The spec creates its event via
the API fixture (`createRegistrationEvent` — a generic CREATED event with a captured
`BATbern{N}` code), skipping the slow/flaky UI create entirely.

**Cleanup contract.** Tasks have NO prefix-sweep entityType (EMS sweep is events/sessions/
topics only). `event_tasks.event_id` is `ON DELETE CASCADE` (V22), so `cleanupByCode` deleting
the throwaway fixture event in `afterAll` removes its tasks. `mode: 'serial'` + one shared
fixture event (created once, torn down once). Verified residue-free on dev (event → 204, final
events sweep `{events:0}`).

**Component testids added** (same commit): `event-tasks-tab-content` (EventTasksTab root) +
per-row `task-template-<templateId>` / `task-assignee-<templateId>` on BOTH the default and
custom template lists. `OrganizerSelect` already forwarded `data-testid` to its inner `<Select>`
and already emitted `organizer-option-<username>` option testids — no change there. Green ×2 on
dev (2/2 both runs); type-check + lint clean; `@smoke`→1 / `@gate`→2 via tag routing. The
admin `TaskTemplatesTab` (managing the template catalog itself) is a separate surface, untouched
by this slice. Staging can't validate the new testids until this PR's frontend deploys (same
deploy-then-green pattern as PRs 2/4/5/8).

### PR 7 notes — sessions/slot-assignment slice (rewrite-to-reality, deterministic non-DnD @smoke)

Slice 6's lone spec (`slot-assignment/slot-assignment-workflow.spec.ts`) was an all-skipped
5-test `test.describe.skip(...)` RED-PHASE TDD block (Story 5.7 / BAT-11) asserting an
**idealized DOM that was never built**. The feature itself IS fully built and routed
(`/organizer/events/:eventCode/slot-assignment` → `SlotAssignmentPage` → `DragDropSlotAssignment`),
so this is a pure rewrite-to-reality of the test, not the feature.

**Tests deleted (asserted testids/flows that do not exist):**
- `should assign via drag-and-drop`: `speaker-card`, `slot-dropzone[data-time]`,
  `assignment-success-toast`, `assignment-progress` ("0 of 3 assigned"). The real cards live in
  `speaker-pool-sidebar` (per-card `drag-handle`), the real drop cells are
  `slot-<HH:MM>-Main-Hall`, there is no success toast, and the count is plain (untestid'd) text.
- `should detect and resolve timing conflicts`: `conflict-detection-modal` + `conflict-type` +
  "Find Alternative Slot"/"Change Room"/"Reassign" resolution buttons + `room-selector`. The real
  conflict surface is `ConflictDetectionAlert` (`conflict-type-badge`/`conflict-severity`), shown
  only on a genuine 409 overlap, with no room-change resolver. Provoking a real overlap
  deterministically needs two slot-aligned assignments — not gate-worthy for the value.
- `should show speaker time preferences during drag`: `speaker-preference-panel`,
  `time-preferences-section`, `preference-match-high/low` class assertions mid-drag. The panel is
  `SpeakerPreferencePanel` (different testids) and is populated from MOCK data
  (`getSpeakerData` is hardcoded in the component) — asserting it tests a stub, not a feature.
- `should use bulk auto-assignment` + `should navigate to publishing tab`: a 3-step
  `bulk-auto-assignment-modal` wizard (`algorithm-balanced`/`assignment-preview-list`/
  `match-score`/"Apply Assignments"), `assignment-complete-banner`, and a `/publishing` tab with
  `publishing-timeline`/`validation-session-timings`. NONE exist — auto-assign is a single
  confirm modal (`auto-assign-modal`→`auto-assign-confirm`); the success banner is an untestid'd
  MUI Alert with an inert `href="#"` link, and there is no `/publishing` route walk here.

**`@smoke` = auto-assign, deliberately NOT drag-drop (reliability, plan risk #3).** Assignment's
primary UI is **native HTML5** drag-and-drop (no dnd-kit), which Playwright's `dragTo` drives
unreliably — wrong for a blocking gate. The deterministic equivalent is the **auto-assign**
button → `auto-assign-modal` → `auto-assign-confirm`, which calls `POST /sessions/auto-assign`
(places every unassigned session into a free slot from the backend timetable) and awaits the
POST + refetch before closing the modal — so modal-hidden is a clean completion signal. That is
this slice's one mutating+cleanup happy path.

**Unassigned-session fixture (the create-then-clear trick).** "Unassigned" = a non-structural
session with `startTime IS NULL`. The REST `POST /events/{code}/sessions` endpoint REQUIRES
`startTime`/`endTime` (`CreateSessionRequest @NotNull`), so a session cannot be born timing-less
through the API. In production these placeholders come from the speaker workflow; reproducing
that via the UI would drag in cron/state transitions. New helper `addUnassignedSessions`
(event-fixture.ts) instead creates each session WITH throwaway timing, then issues one
`DELETE /events/{code}/sessions/timing` to clear ALL timings — leaving them unassigned. The
read-only `@gate` needs no sessions at all: the slot GRID is computed from the EVENING event-type
config (4 speaker slots + a break, seeded Flyway V10), so `slot-<HH:MM>-Main-Hall` cells render
for any EVENING event. Slot-cell times are rendered in the browser timezone (`toTimeStr` over the
backend slot start) → matched by testid SHAPE (`/^slot-\d{1,2}:\d{2}-Main-Hall$/`), never a
hardcoded time.

**Two tests, zero new testids** (SlotAssignment was already richly testid'd):
- `should_renderSlotAssignmentLayout_when_pageOpened` (`@gate`, read-only): the three-column
  layout (`speaker-pool-sidebar`/`session-timeline-grid`+`timeline-grid`/`quick-actions-panel`),
  both quick-action buttons, and ≥1 droppable `slot-*` cell (proves the grid is wired to the
  event-type slot template, not an empty shell).
- `should_assignAllSessions_when_autoAssignConfirmed` (**`@smoke`**+`@gate`): seed 2 unassigned
  sessions via the API → assert 2 `drag-handle` cards in the sidebar → auto-assign → assert the
  modal closes and the sidebar shows `empty-state` → **authoritative verify**
  `GET /sessions/unassigned == 0` (the assignment actually persisted, stronger than the UI alone).

**Cleanup contract.** One throwaway EVENING event per file (serial, shared) created via the API
fixture (`createRegistrationEvent`, captured `BATbern{N}`) and deleted in `afterAll`
(`cleanupByCode`). `sessions.event_id` (V2) and `session_timing_history.session_id` (V28) are
`ON DELETE CASCADE`, so the event delete removes every session + timing row; the server-generated
`sessionSlug` isn't reachable by the `bruno-test-session-` prefix sweep, so the parent-event
delete is the only teardown (plan §A5 server-generated-code caveat). Serial mode + one shared
event means the read-only `@gate` renders the bare event first, then the `@smoke` adds + assigns
its own sessions — no cross-test race. Green ×2 on dev (2/2 both runs); final global-teardown
sweep `events:0 sessions:0` (residue-free); tsc + eslint clean. Zero `src/` component changes, so
no unit tests affected. Unlike testid-adding slices, the new specs go green on staging
immediately post-deploy (no new build artifact needed — only e2e files changed).

**Local-run footgun (noted for the next author):** `--slice "Slot Assignment"` (a positive
case-sensitive `--grep`) also matches the heavy `event-lifecycle-e2e` "Phase D — Slot Assignment
& Publish Agenda" test, which is slow and currently red on dev (slice-10/PR-11 territory, not
touched here). Run this slice with `--slice "Story 5.7"` to scope to these two specs only.

### CI fix (2026-05-30) — Playwright teardown sweep raced the concurrent Bruno job

First PR-691 CI run: `bruno-tests` failed `users-api` → auto-rollback fired. Root cause was
**self-inflicted**, not a Bruno regression. `playwright-tests` and `bruno-tests` both
`needs: deploy-to-staging`, so they ran **concurrently** against the shared staging (= prod)
DB. Playwright's `global-teardown` runs the canonical prefix sweep (`bruno.test*`,
`BRUNOTESTCO*`, …) it **deliberately shares** with Bruno (§A3/§A4 — one cleanup contract).
Timeline: Bruno created `bruno.test` (17:24:11) → Playwright sweep deleted it
`{"user_profiles":1}` (17:24:25) → Bruno's next GET failed `expected 404 to equal 200`
(17:24:27). Verified Bruno `users-api` is green 46/46 when run alone.

**Fix:** `playwright-tests` now `needs: [deploy-to-staging, bruno-tests]` — the two suites are
serialised so the sweep can never fire while Bruno is live. Playwright runs only on
Bruno-green (a failed `needs` skips it), which is also the correct gate-flip topology
(deploy → bruno → playwright → promote/rollback). The shared-prefix design (§A4) is sound;
it just must never run concurrently with another suite that sweeps the same prefixes.

### PR 1 deviations from the §A prose (recorded so §A reads as built)

1. **Edge-readiness poll (§A1) is asset-coherence, NOT SHA/`/version` match.** The frontend
   is "build once, deploy everywhere" (runtime config via `GET /api/v1/config`), carries no
   embedded git SHA / `/version` marker, and is built *selectively* (only when the frontend
   changed). A SHA-match poll is therefore architecturally impossible (a backend-only deploy
   never changes the asset hash). The runner instead polls `index.html` (200) → extracts the
   hashed entry asset → fetches it (200), backoff ~5 min. Verified: `/assets/index-*.css → 200`
   on the staging run. This is the strongest build-agnostic edge signal available.
2. **EMS cleanup allowlist is narrower than the §A4 prose.** The deployed
   `TestFixtureCleanupService` enums accept only: CUMS `companies|users|additional_emails`,
   EMS `events|sessions|topics`, PCS `partners|meetings`. The §A4 narrative's
   "tasks/registrations/uploads" have NO prefix-sweep path — those slices clean up by
   explicit delete or via the event-delete cascade. `test-fixtures-cleanup.ts` mirrors the
   REAL allowlist (`SWEEP_TARGETS`).
3. **`scripts/ci/run-playwright-tests.sh` already existed** (it was marked NEW). Enhanced in
   place: kept its working token/refresh logic, added `--scope`/`--slice`/`--cleanup-only`,
   the edge poll, and tag-grep routing. Added a `--scope quarantine` value (not in §A6) for
   the nightly flake-promotion re-test.
4. **Opportunistic bugfix:** `organizer/speaker-onbehalf-vs-self-byte-identity.spec.ts:76`
   used `async (_, testInfo)` — Playwright requires the first test arg to be object-
   destructured, so it threw at *collection* time and poisoned discovery for the ENTIRE
   chromium project (no test could run, incl. `@smoke`). Fixed to `async ({}, testInfo)`.
   Pre-existing (slice-8 spec); fixed here because it blocked the PR-1 gate outright.

Also: the now-orphaned `get-test-tokens` step in `deploy-to-staging` (its only consumer was
the dormant Playwright step) was removed; each test job re-fetches its own fresh tokens. The
`playwright-tests` job writes `~/.batbern/staging-{role}.json` itself (id+access+refresh) so
`global-setup.ts` can build browser storage state — CI never had those files before because
the dormant step never ran.

## Handoff — recon + landmines for the remaining slices (2026-05-31)

A parallel read-only recon mapped slices 10–13 against reality. Key findings + the traps that
the optimistic plan estimates miss (so the next session doesn't rediscover them). Cross-cutting
lesson from slices 8/9: **promote-to-READY provisions a Cognito/CUMS user out-of-band and is
intermittently flaky on dev (observed 500s) — keep it out of any blocking `@smoke`.**

- **Slice 11 — partners + meetings** (medium; needs ORGANIZER + PARTNER tokens). Specs:
  `partner-management/{partner-directory,partner-create-edit}`, `organizer/partner-meetings`,
  `partner/{analytics-dashboard,topic-voting}`. Landmines: (a) `partner/analytics-dashboard`
  asserts a **fictional `attendance-table` testid** — the component renders Recharts charts, not
  a table → it would time out; rewrite to assert `attendance-dashboard`/`kpi-attendance-rate`/
  `kpi-cost-per-attendee` + `export-button` (all real). (b) `partner-create-edit` uses inline
  `tc-{ts}` company names (NOT swept) + manual `deletePartnerViaAPI` (residue source) and expects
  route `/partners/.+` but reality is `/organizer/partners/:companyName`; switch to
  `factory.partnerName()` (`brtest`+6 = 12 chars, swept) + `sweepAllPrefixes`/`cleanupById`.
  (c) `partner-directory` uses `getByLabel(/grid view/i)` (localized) + fictional
  `tier-option-*`/`status-option-*` MenuItem testids. Testid adds: `tier-option-{tier}`,
  `status-option-{status}` on PartnerFilters MenuItems; `partnership-start-date`/`-end-date` on
  the date inputs; `partner-edit-company-name`; `topic-form-title-input`. `partner-meetings` is
  fully mocked + already testid-clean (keep `@gate`). **@smoke**: organizer partner create →
  detail → cleanup (factory name + sweep) — deterministic, no promote.

- **Slice 13 — cross-cutting a11y/auth/cors** (light-ish, but two traps). `accessibility/
  {navigation,screen-reader,layout}` use `getByRole`/axe scans — **a11y locators are exempt** from
  the testid-only rule (they assert user-facing semantics); tag `@gate` AFTER confirming the axe
  scans actually pass on dev (real WCAG violations would fail them — budget for either a fix or
  `@quarantine`, don't assume green). Add `data-testid="notifications-button"` to `AppHeader` and
  convert the one notifications `getByRole` locator. Delete the 2 truly-fictional screen-reader
  skips (on-submit form-error announcement; forced-colors high-contrast) + the nav
  notification-dropdown skip (notifications are inline, no dropdown). `api-integration/
  {cors-validation,companies-api-integration}` are exemplary → tag `@gate`. **Correction to the
  recon:** the forgot/reset-password components (`src/components/auth/{ForgotPasswordForm,
  ResetPasswordForm}`) DO exist and DO carry testids — the `auth/*` specs aren't fictional, they
  need testid reconciliation; treat as a separate focused task, out of slice 13's core. No `@smoke`
  (all read-only).

- **Slice 10 — event workflow** (medium post-rewrite; ORGANIZER). `workflows/event-lifecycle-e2e`
  is currently RED. The transition endpoint EXISTS: `PUT /events/{code}/workflow/transition`
  `{ targetState, overrideValidation, overrideReason }` (EventWorkflowController) + `GET
  /events/{code}/workflow/status`. Rewrite per OQ-3 to **hybrid: API force-advances states
  (overrideValidation:true), UI asserts each screen** — DELETE the manual-mouse drag-drop phases
  (B3–B4) and the speaker/content phases (slice-8 territory). Testid corrections: lane names are
  **lowercase** (`status-lane-ready`, not `-READY`); assert `workflow-status-badge` after each
  transition. All needed testids exist (`event-*-field`, `event-tab-*`, `status-option-{STATE}`,
  `override-workflow-validation-checkbox`, `publish-{phase}-button`). **@smoke**: create →
  GET status=CREATED → transition TOPIC_SELECTION → AGENDA_PUBLISHED → ARCHIVED (each UI-asserted)
  → DELETE. Cleanup = `cleanupByCode` (BATbern{N}; 409 until ARCHIVED). ~15 min, no cron, no DnD.

- **Slice 12 — admin tabs** (HEAVIEST — testid-adding; ORGANIZER). `organizer/admin-settings`
  currently covers only AdminSettingsTab with `getByRole`/`Date.now()`. Reality: 9 tabs +
  OrganizerAnalyticsPage + NotificationsPage. **Prod-safety is the crux** — most admin mutations
  are GLOBAL singletons with NO restore path (event-types, presentation-settings, ai-prompts,
  admin-settings/email-forwarding, email-templates): these MUST stay **read-only `@gate`** (same
  reason slice 4 dropped event-type PUTs). Only TWO safe mutations exist for `@smoke`:
  TaskTemplatesTab (create+delete a custom template by captured id) and GlobalImagesTab
  (upload+delete by captured imageId). Plan: ~11 read-only `@gate` tab-render tests + ≤2 safe
  `@smoke`. Testid gaps to add (~25): PresentationSettingsTab, AiPromptsTab, AdminSettingsTab,
  OrganizerAnalyticsPage, NotificationsPage have essentially none (EventTypes/Import/TaskTemplates/
  EmailTemplates/GlobalImages/VenueCatering already have some). Budget a full session.

Suggested remaining PR grouping: **PR C = 11 + 13**, **PR D = 10 + 12**, then **PR 15 = gate flip**
(+ deliberate-fail rollback drill). Rebase each onto develop as the prior stacked PR merges.

## Quality bar (binding for every slice that gets touched)

Per PO directive 2026-05-30 — when a slice is audited, it leaves in this state or it doesn't merge:

1. **Locators: `data-testid` only.** No CSS attribute/tag selectors, no `getByText`/text
   matching, no `nth()` positional locators for app elements. Use `page.getByTestId(...)`.
   Add the testid to the component (kebab-case, dynamic IDs via template literals, matching
   the existing convention in `PartnerList.tsx`) when one is missing. The component change and
   the test change land in the same commit.
2. **One test-data naming convention.** All specs consume `e2e/helpers/test-data-factory.ts`
   (A5) — no inline `Date.now()` titles, no `"E2E Test …"` strings. The factory emits the
   canonical Bruno-shared prefixes so both suites share one cleanup contract.
3. **No empty tests.** Every `test.fixme()` / `test.skip()` / zero-assertion placeholder is
   either **written** (real assertions, real cleanup) or **deleted**. A spec file with no
   active `test()` is removed. This directly retires the three speaker-portal stubs.
4. **Cleanup is mandatory.** Every mutating test cleans up its own data (explicit delete by
   captured code/id, or prefix sweep) AND `global-teardown` runs a final sweep. A spec that
   mutates without teardown does not merge.

## A. Infrastructure foundation (PR 1)

Nothing can gate until these exist. Mirrors Bruno's infra PR predating any audit pass.

### A1. New `playwright-tests` CI job (mirror of `bruno-tests`)
Promote the dormant step (`deploy-staging.yml:1286-1331`) into its **own job**
`playwright-tests`, `needs: deploy-to-staging`, copying `bruno-tests` structure (`:1378`+):
checkout → `configure-aws-credentials` → the identical `get-test-tokens` step (re-fetch
Cognito tokens fresh — 60-min ID-token TTL vs a 20–30 min deploy, same rationale as Bruno's
`:1401` comment). Run `scripts/ci/run-playwright-tests.sh staging` (A7). Keep the
`Upload Playwright test results` artifact step inside the job with `if: always()`.

**Timing difference vs Bruno (critical):** Bruno needs only the backend; Playwright needs
**backend ECS stable AND frontend (S3/CloudFront) live.** `deploy-to-staging` already runs
`aws ecs wait services-stable` (`:1052`) and the CDK/S3 frontend deploy, so `needs:
deploy-to-staging` covers both. The open gap is **CloudFront propagation/invalidation**, so
the job MUST (OQ-2 resolved 2026-05-30: yes, poll) start with an explicit edge-readiness poll
— GET `https://www.batbern.ch` and assert the freshly-deployed asset hash / a `/version`
marker matches `github.sha`, retrying with backoff up to ~5 min — before launching any
browser. Without it, tests can hit stale CloudFront assets right after a deploy and produce
spurious reds.

### A2. Wire into the EXISTING tag-stable / rollback jobs — incrementally
Reuse `promote-ecr-tag.sh` and `rollback-deployment.sh` **unchanged** (tag/task-def agnostic).
The requirement: **Bruno must stay authoritative while Playwright is onboarded.**

- **During onboarding (PRs 2…14):** `playwright-tests` runs as its own job but runs only the
  `@smoke`-tagged subset and does **not** feed promotion/rollback. `tag-stable-on-success` stays
  `if: needs.bruno-tests.result == 'success'`; `rollback-on-bruno-failure` stays
  `if: always() && needs.bruno-tests.result == 'failure'`.
- **At gate-flip (PR 15):** change both downstream jobs to AND of both test jobs:
  - `tag-stable-on-success`: `needs: [deploy-to-staging, bruno-tests, playwright-tests]`,
    `if: needs.bruno-tests.result == 'success' && needs.playwright-tests.result == 'success'`.
  - `rollback-on-bruno-failure` → rename `rollback-on-test-failure`:
    `if: always() && (needs.bruno-tests.result == 'failure' || needs.playwright-tests.result == 'failure')`.
    The `always()` is load-bearing (a failed `needs` skips the job before `if:` evaluates —
    same reason documented at `:1543`).

### A3. `e2e/global-teardown.ts` (NEW — none exists)
Add `globalTeardown: './e2e/global-teardown.ts'` to `playwright.config.ts`. It calls the
shared cleanup helper (A4) to run a **final prefix sweep** across all three cleanup services
for every canonical prefix, per available role token — the belt-and-suspenders role Bruno's
`99-posttest-cleanup.bru` plays. Catches residue from any spec that crashed before its own
teardown.

### A4. Shared cleanup helper (NEW — `e2e/helpers/test-fixtures-cleanup.ts`)
Typed client POSTing to `cums` (companies/users), `ems`
(events/sessions/topics/tasks/registrations/uploads), `pcs` (partners + meetings id-allowlist),
body `{ entityType, prefix }` exactly as `bruno-tests/sessions-api/99-posttest-cleanup.bru`,
accepting `[200,204,404]`. Exports: `sweepAllPrefixes(token)` (teardown),
`cleanupByCode(token, eventCode)` / `cleanupById(token, type, id)` (explicit-delete path).
**Supersede** the ad-hoc `e2e/workflows/documentation/helpers/api-helpers.ts`
`cleanupTestEvent()`/`cleanupOrphanedTestEvents()` (which match `"E2E"` in title — the source
of the `"E2E Test Company"` prod residue the Bruno audit found); have the doc helpers delegate
to the new helper.

### A5. Canonical test-data factory (NEW — `e2e/helpers/test-data-factory.ts`)
Emits the **same canonical prefixes Bruno uses** so both suites share one cleanup contract:
company `BRUNOTESTCO<ts>`, username `bruno.test.<ts>`, email
`bruno-test-<ts>@e2e.batbern.invalid`, topicCode `bruno-test-topic-<ts>`, partner `brtest<id>`,
file `bruno-test-<ts>.png`.

**Critical caveat (server-generated codes):** `eventCode` and `sessionSlug` are
**server-generated** (`CreateEventRequest` has no `eventCode`; server derives `BATbern{N}`;
sessions slugify from title). Prefix-sweep cleanup is therefore a **no-op** for events/sessions
created via the real UI flow. Teardown for those entities must be **explicit DELETE by the
code/id captured from the UI/API response** (accepts `[204,404,409]`; 409 disappears once the
event is ARCHIVED → deletable), exactly as Bruno does in
`bruno-tests/event-full-workflow-api/98-delete-fixture-event.bru`. The factory returns a handle
the spec captures and feeds to `cleanupByCode` in `afterEach`/`afterAll`. Seed a recognizable
event-title token (e.g. `BATPW-E2E`) so the teardown sweep can find orphaned events by title
even though the code is server-generated.

### A6. Tagging scheme — `@smoke` / `@gate` / `@quarantine` (OQ-1 resolved)
Three Playwright tags drive the two run scopes:
- `@smoke` — the curated **per-deploy** gate: one mutating+cleanup happy path per entity
  (~30–60 tests total). Every `@smoke` test is also `@gate`. Fast, low-flake, blocking on
  every deploy.
- `@gate` — proven green ×2, part of the **full nightly** suite. A slice "joins the gate"
  purely by tagging its stable specs `@gate` (and marking its one canonical path `@smoke`) —
  no per-slice CI edit.
- `@quarantine` — known-flaky, excluded everywhere, tracked in the progress log.

Two run scopes (no per-slice CI edit; tags do the routing):
- **Per-deploy** (`playwright-tests` job): `--grep @smoke --grep-invert @quarantine`.
- **Nightly** (new scheduled workflow, A9): `--grep @gate --grep-invert @quarantine`.

Locally the default run is everything.

### A7. Runner script (NEW — `scripts/ci/run-playwright-tests.sh`, analogue of `run-bruno-tests.sh`)
`run-playwright-tests.sh <env> [--project chromium|speaker|partner] [--slice <grep>] [--scope smoke|gate|all] [--cleanup-only]`.
Sets `TEST_ENV`, fetches/injects role tokens (reuse `scripts/auth/get-token.sh`), and routes
the tag grep via `--scope` (`smoke` → `--grep @smoke --grep-invert @quarantine`; `gate` →
`--grep @gate --grep-invert @quarantine`; `all` → everything, the local default). `--slice`
runs one entity slice at a time (the "one-by-one" loop). `--cleanup-only` invokes only the
`global-teardown` sweep — belt-and-suspenders after a runner crash, like Bruno's `--cleanup-only`.

### A8. Enable the dormant step
Delete the `if: false` / `continue-on-error: true` step at `:1292` (logic moves into the new
job per A1). Until PR 15 the new job is non-blocking (A2 onboarding stage).

### A9. Nightly full-suite workflow (NEW — `.github/workflows/nightly-e2e.yml`)
A scheduled (`cron`) workflow that runs `run-playwright-tests.sh staging --scope gate` against
deployed staging — the full proven `@gate` suite plus a re-test of `@quarantine` specs (so
flakes that have settled can be promoted back). Non-blocking on deploys; posts a summary +
uploads the report. This is where "everything gates" lives (OQ-1): the per-deploy gate stays
the fast `@smoke` subset, the nightly run exercises the whole proven suite daily. Lands in
PR 1 (empty/near-empty until slices start tagging `@gate`).

**PR 1 exit criteria:** the infra job runs the `@smoke` set (initially one trivial smoke spec)
green ×2 against staging; the edge-readiness poll (A1) is in place; the nightly workflow (A9)
exists; teardown sweep verified to delete a deliberately-seeded fixture row; Bruno gate
untouched and still authoritative. Plan doc lands here as
`docs/plans/playwright-staging-hardening.md`.

## B. Decomposition — N/A

Bruno had one monolithic `events-api` collection to split. The Playwright suite is **already
file-per-feature** (58 specs across `e2e/workflows/*`, `e2e/organizer`, `e2e/partner`,
`e2e/speaker`). No decompose phase. The unit of work is the **entity slice** (a small set of
specs), not a folder split.

## C. Per-entity audit pass, leaf-first (PRs 2–14)

Dependency-ordered so each slice's prerequisites already gate (mirrors Bruno's
file-upload→companies→users→…→speaker-pool→workflow→partners order). Leaf entities first;
composites last.

| # | Slice | Specs | Cleanup svc / prefix | data-testid gaps to fill | Weight |
|---|-------|-------|----------------------|--------------------------|--------|
| 1 | uploads | `user-account/photo-upload` | `ems uploads` / `bruno-test-<ts>.png` | — | light |
| 2 | companies | `company-management/{company-creation,company-search}`, `api-integration/companies-api-integration` | `cums companies` / `BRUNOTESTCO<ts>` | — | light — **fixes prod residue** |
| 3 | users | `user-management/*`, `user-sync/*`, `user-account/{profile-management,settings-management,user-settings-additional-emails}` | `cums users` / `bruno.test.<ts>` | — | medium |
| 4 | topics + event-types | `organizer/{topic-selection,blob-topic-selector,event-type-selection}` | `ems topics` / `bruno-test-topic-<ts>` | `TopicManagementPage`, `EventTypesTab` (zero) | heavy |
| 5 | tasks | `tasks/{test-task-creation-from-templates,test-task-assignment}` | `ems tasks` | `TaskTemplatesTab` (zero) | medium |
| 6 | sessions/slot | `slot-assignment/slot-assignment-workflow` | `ems sessions` (explicit-delete, server-gen slug) | — (SlotAssignment strong) | medium |
| 7 | registrations | `registration-flow`, `presentation`, `archive-*` | `ems registrations` | — | light (read/public) |
| 8 | speaker pool (organizer) | `organizer/speaker-{brainstorming,outreach,invitation,kanban-guided-drag,column-triage,card-primary-action,onbehalf-vs-self-byte-identity}` | `ems speaker_pool` | — (SpeakerBrainstorming strong) | medium |
| 9 | speaker portal | `speaker/*` | `ems speaker_pool` | — | heavy — **net-new** for `speaker-portal-{respond,content-submit,cross-portal-nav}` + `magic-link-teardown` fixmes; needs `SPEAKER_AUTH_TOKEN` |
| 10 | event workflow | `event-lifecycle-e2e` + new walk (see C-bis) | explicit-delete by `BATbern{N}` | — | heavy |
| 11 | partners + meetings | `partner-management/{partner-directory,partner-create-edit}`, `organizer/partner-meetings`, `partner/{analytics-dashboard,topic-voting}` | `pcs partners` / `brtest<id>` + meetings id-allowlist | — (PartnerManagement strong) | heavy — **no cleanup today**; needs `PARTNER_AUTH_TOKEN` |
| 12 | admin tabs | `organizer/admin-settings` (8 `EventManagementAdminPage` tabs) | per-tab entity | `EventTypesTab, ImportDataTab, TaskTemplatesTab, EmailTemplatesTab, PresentationSettingsTab, AiPromptsTab, GlobalImagesTab, VenueCateringContactsTab`, `OrganizerAnalyticsPage`, `NotificationsPage` (all zero) | **heaviest** |
| 13 | cross-cutting | `accessibility/*`, `auth/*`, `api-integration/cors-validation` | — | — | light (non-mutating) |

### Repeatable per-slice checklist (the "one-by-one" loop) — one stacked PR per slice
1. **Prove existing specs green** locally ×1 against staging (`run-playwright-tests.sh staging --slice <name>`).
2. **Apply the quality bar** (above): every locator → `data-testid` (add the testid to the
   component in the same commit); replace inline test data with the factory (A5); **write or
   delete** every empty/fixme test; remove any spec file left with no active `test()`.
3. **Replace ad-hoc cleanup** with the canonical helper (A4): delete `"E2E"`-match deletes;
   switch `afterEach`/`afterAll` to `cleanupByCode`/`sweepAllPrefixes`. Events/sessions use
   explicit captured-code DELETE (A5 caveat).
4. **Tighten waits**: explicit `await expect(...).toBeVisible()` instead of fixed timeouts.
5. **Run green ×2** locally. Any spec that won't go green ×2 → tag `@quarantine`, log it; do
   **not** block the slice (but a quarantined test still must meet the quality bar — no empty
   tests parked in quarantine).
6. **Admit to gate**: tag the slice's stable specs `@gate`, and tag its single canonical
   mutating+cleanup happy path `@smoke` (so it joins the per-deploy gate; the rest join the
   nightly full run). CI picks both up automatically via tag routing (A6). Update the progress
   log row.

## C-bis. Event-workflow slice — design question (OQ-3, deferred to PR 11)

> **Deferred** (PO, 2026-05-30): resolve OQ-3 when slice 10 (PR 11) is picked up, not now.
> The recommendation below is the architect's default; revisit with fresh eyes at PR 11.


Driving `CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT →
AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED`
(`shared-kernel/.../EventWorkflowState.java`) through the **UI** is the hard part: several
transitions are **automatic** (event listeners + cron), so a pure-UI walk stalls on cron,
blowing the wall-clock budget and adding flake.

**Recommendation:** for the gate, reuse the test-only override Bruno uses —
`PUT /events/{code}/workflow/transition` with `overrideValidation: true` — to force-advance
state between UI assertions (UI validates each screen without depending on cron timing; the
walk is API-driven, UI-asserted — a deliberate hybrid). Keep a pure-UI walk in the opt-in
`documentation`/`screencast` projects (already excluded from `chromium` via `testIgnore`).
Teardown: explicit DELETE of the captured `BATbern{N}` code in `afterAll` (`[204,404,409]`).
See OQ-3.

## D. Gate flip (PR 15)
Apply A2 gate-flip wiring (downstream jobs AND-gate on `playwright-tests`); remove any
remaining `continue-on-error` from the run step. Precondition: every slice has a canonical
`@smoke` path and its remaining specs are either `@gate` (proven ×2) or explicitly
`@quarantine` (tracked). The **per-deploy** gate runs `--grep @smoke --grep-invert @quarantine`
(fast, blocking); the **nightly** workflow (A9) runs `--grep @gate --grep-invert @quarantine`.
Mirror Bruno PR-14's inline comment explaining why `always()` is required on the rollback job.

## E. Verification (PR 15)
1. **Dry-run rollback:** `scripts/ci/rollback-deployment.sh staging --yes --dry-run` confirms
   `staging-stable` → digest resolution works with the new wiring (no script change).
2. **Deliberate-fail drill:** on a throwaway branch, add a guaranteed-red `@smoke` spec, push,
   run `workflow_dispatch` of staging deploy from that branch → confirm: deploy succeeds →
   `playwright-tests` fails → `tag-stable-on-success` skipped (staging-stable unmoved) →
   `rollback-on-test-failure` fires and rolls ECS to `staging-stable`. Low-traffic window
   (BATbern ~3 events/year). Analogue of Bruno F1.
3. Confirm `@quarantine` specs do **not** fail the gate.

## F. Local pre-flight (every PR)
`playwright.config.ts` already supports `TEST_ENV=development` (localhost:8100). Every stacked
PR runs its slice locally green ×2 via `run-playwright-tests.sh development --slice <name>`
before push, references the plan doc, updates the progress log. Same discipline as Bruno's
Section G. Prerequisite: Pattern 3b user mirroring (`./scripts/dev/sync-users-from-cognito.sh`)
so staging-issued JWTs resolve roles against the local DB.

## Critical files

| Path | Change |
|------|--------|
| `.github/workflows/deploy-staging.yml` (≈1286-1576) | Dormant step → new `playwright-tests` job; AND-gate `tag-stable-on-success` / rename+widen `rollback-on-bruno-failure` |
| `web-frontend/playwright.config.ts` | Add `globalTeardown`; `@gate`/`@quarantine` grep wiring; revisit `workers:1` (now its own job) |
| `web-frontend/e2e/global-teardown.ts` (new) | Final cleanup sweep |
| `web-frontend/e2e/helpers/test-fixtures-cleanup.ts` (new) | Typed client for `/admin/test-fixtures/{cums,ems,pcs}/cleanup` |
| `web-frontend/e2e/helpers/test-data-factory.ts` (new) | Canonical prefixes shared with Bruno |
| `web-frontend/e2e/workflows/documentation/helpers/api-helpers.ts` | Supersede `"E2E"`-match cleanup; delegate to new helper |
| `scripts/ci/run-playwright-tests.sh` (new) | Runner with `--project`/`--slice`/`--cleanup-only` |
| `web-frontend/src/components/organizer/Admin/*Tab.tsx`, `pages/organizer/{TopicManagementPage,OrganizerAnalyticsPage,NotificationsPage}.tsx`, `EventPage/*Tab.tsx` | Add `data-testid` per slice (same commit as the spec) |
| `docs/plans/playwright-staging-hardening.md` (new) | This plan, checked in (PR 1) |

## Reuse (no rebuild)
- `scripts/ci/promote-ecr-tag.sh`, `scripts/ci/rollback-deployment.sh` — tag/task-def agnostic.
- `/api/v1/admin/test-fixtures/{cums,ems,pcs}/cleanup` endpoints + canonical prefix regexes.
- The `get-test-tokens` CI step + `scripts/auth/get-token.sh`/`refresh-token.sh` for role tokens.
- `web-frontend/e2e/global-setup.ts` Cognito-token-injection pattern (mirror for teardown auth).
- `bruno-tests/.../98-delete-fixture-event.bru` — explicit-DELETE teardown pattern for server-generated codes.
- `docs/plans/bruno-staging-hardening.md` — structural template.

## Risks to track
1. **Prod blast radius of UI mutations.** Every mutating spec runs against www/api.batbern.ch
   (real prod). Teardown discipline is the only safety net; a failed teardown leaves residue —
   exactly the problem the Bruno audit found. `global-teardown` sweep + per-spec explicit deletes.
2. **Server-generated `eventCode`/`sessionSlug`** break prefix-sweep cleanup → must use
   explicit captured-code DELETE; a spec that doesn't capture the code leaks an
   undeletable-by-sweep event. Title-token (`BATPW-E2E`) sweep is the orphan backstop.
3. **Flakiness → spurious rollbacks.** Even 99.5%/test over 411 tests ≈ 12% chance of ≥1
   spurious red. Mitigated (OQ-1 resolved): the per-deploy gate is the small `@smoke` subset,
   not the full suite, so the blocking-on-deploy flake surface is ~30–60 tests; `retries:2` +
   quarantine discipline cover the rest. The full suite's flake surface is absorbed by the
   non-blocking nightly run (A9).
4. **Frontend-deploy timing.** CloudFront propagation/invalidation not guaranteed when ECS is
   stable; tests may hit stale assets. Mitigated (OQ-2 resolved): mandatory edge-readiness poll
   at the top of the job (A1).
5. **Speaker/partner token availability in CI.** `speaker`/`partner` projects only activate when
   `SPEAKER_AUTH_TOKEN`/`PARTNER_AUTH_TOKEN` are set; `get-test-tokens` warns-and-skips on
   missing creds → those slices silently don't gate. Confirm `STAGING_SPEAKER_*` /
   `STAGING_PARTNER_*` secrets exist before slices 9 and 11.
6. **Wall-clock.** The full ~411-test suite at `workers:1` + retries is 45–90+ min. Mitigated
   (OQ-1 resolved): only the `@smoke` subset runs per deploy; the full suite runs nightly where
   wall-clock is free. Still worth raising `workers` above 1 in the dedicated nightly job.
7. **Cognito 60-min token TTL** vs long full-suite runtime — the nightly full run can outlive
   the ID token. Mid-run re-auth needed for nightly; the per-deploy `@smoke` subset is short
   enough to fit one token.

## Open Questions

- **OQ-1 — per-deploy gate scope. ✅ RESOLVED 2026-05-30:** curated `@smoke` subset per deploy
  (one mutating+cleanup path per entity, ~30–60 tests, blocking + rollback) + full `@gate`
  suite on a nightly schedule (A9, non-blocking). "Everything gates" is honored as the daily
  nightly run, not on every deploy — keeps deploys fast and rollbacks meaningful.

- **OQ-2 — edge-readiness gate. ✅ RESOLVED 2026-05-30:** yes — the `playwright-tests` job
  polls `www.batbern.ch` for the freshly-deployed asset hash / `/version` marker (matching
  `github.sha`) with backoff before launching browsers (A1).

- **OQ-3 — event-workflow walk: hybrid vs pure-UI. ⏸ DEFERRED to PR 11** (PO, 2026-05-30).
  Decide when slice 10 is picked up. Architect's default: hybrid (use the `overrideValidation`
  transition endpoint to force-advance cron/auto states — fast, low-flake, API-driven +
  UI-asserted), with the pure-UI walk kept in the opt-in documentation project. Revisit with
  fresh eyes at PR 11.

## Verification (end-to-end, after PR 15)
1. Push a trivial change to `develop` → `deploy-staging.yml` runs; confirm `playwright-tests`
   is now a blocking job and `tag-stable-on-success` requires both Bruno and Playwright green.
2. Check ECR: `staging-stable` = previous SHA, `staging-current` = new SHA after a green deploy.
3. Deliberate-fail drill (Section E.2) → confirm rollback runs and the ECS task defs pin to
   `staging-stable`.
4. Query prod DB for canonical-prefix rows (`BRUNOTESTCO%`, `bruno.test.%`, `brtest%`,
   `BATPW-E2E%` events) after a full run → confirm 0 residue (teardown works).
5. Cleanup audit logs in CloudWatch show no anomalies.
