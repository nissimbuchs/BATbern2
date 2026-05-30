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

> **Where we are (2026-05-30):** Plan drafted, not yet started. Bruno gate is live and
> authoritative. Playwright is disabled (`deploy-staging.yml:1292` `if: false`). The
> gate/rollback machinery (`rollback-deployment.sh`, `promote-ecr-tag.sh`, `staging-stable`
> ECR tag, `tag-stable-on-success` / `rollback-on-bruno-failure` jobs) and the cleanup
> endpoints (`/api/v1/admin/test-fixtures/{cums,ems,pcs}/cleanup` + canonical prefixes) all
> exist and are reused unchanged. **NEXT:** PR 1 (infra) per Section A.

Update this one line on every PR merge so a fresh session can pick up without re-reading the whole plan.

## Progress log

This plan is **not** tracked as BMad stories — it's test-infrastructure work + opportunistic
bugfixes. The table below is the single source of truth.

**PR description convention:** every PR includes `Refs: docs/plans/playwright-staging-hardening.md PR #<n>`
and the final commit updates its row (status, merged date, findings/bugs). Bugs found during a
slice audit land as separate fix-commits in the same PR.

| PR # | Branch | Slice / scope | Specs touched | data-testid gaps filled | Green ×2 | Gate tag | Status | Findings |
|------|--------|---------------|---------------|-------------------------|----------|----------|--------|----------|
| 1 | `e2e-staging-hardening-infra` | A+B infra: `playwright-tests` job (+ edge-readiness poll), `global-teardown`, cleanup helper, test-data factory, runner script (`--scope`), `@smoke`/`@gate`/`@quarantine` scheme, nightly workflow (A9), enable dormant step (non-blocking) | — | — | — | — | ⬜ | — |
| 2 | `e2e-uploads` | Slice 1: file-upload/uploads | `user-account/photo-upload` | — | — | — | ⬜ | — |
| 3 | `e2e-companies` | Slice 2: companies | `company-management/*`, `api-integration/companies-api-integration` | — | — | — | ⬜ | fixes `"E2E Test Company"` prod residue |
| 4 | `e2e-users` | Slice 3: users | `user-management/*`, `user-sync/*`, `user-account/{profile,settings,additional-emails}` | — | — | — | ⬜ | — |
| 5 | `e2e-topics` | Slice 4: topics + event-types | `organizer/{topic-selection,blob-topic-selector,event-type-selection}` | `TopicManagementPage`, `EventTypesTab` | — | — | ⬜ | heavy (zero testids) |
| 6 | `e2e-tasks` | Slice 5: tasks | `tasks/*` | `TaskTemplatesTab` | — | — | ⬜ | — |
| 7 | `e2e-sessions` | Slice 6: sessions/slot-assignment | `slot-assignment/slot-assignment-workflow` | — | — | — | ⬜ | server-gen sessionSlug → explicit-delete |
| 8 | `e2e-registrations` | Slice 7: registrations + archive | `registration-flow`, `presentation`, `archive-*` | — | — | — | ⬜ | mostly read/public |
| 9 | `e2e-speaker-pool` | Slice 8: speaker pool (organizer) | `organizer/speaker-*` | — | — | — | ⬜ | strong testids already |
| 10 | `e2e-speaker-portal` | Slice 9: speaker portal | `speaker/*` | — | — | — | ⬜ | **net-new** for 3 fixme stubs; needs `SPEAKER_AUTH_TOKEN` |
| 11 | `e2e-event-workflow` | Slice 10: full workflow create→archive | `event-lifecycle-e2e`, workflow walk | — | — | — | ⬜ | **heavy** — see Section C-bis |
| 12 | `e2e-partners` | Slice 11: partners + meetings | `partner-management/*`, `organizer/partner-meetings`, `partner/*` | — | — | — | ⬜ | **no cleanup today** (residue source); needs `PARTNER_AUTH_TOKEN` |
| 13 | `e2e-admin-tabs` | Slice 12: organizer admin tabs | `organizer/admin-settings` | 8 admin tabs, `OrganizerAnalyticsPage`, `NotificationsPage` | — | — | ⬜ | **heaviest** — zero testids |
| 14 | `e2e-cross-cutting` | Slice 13: a11y/auth/cors sweep | `accessibility/*`, `auth/*`, `api-integration/cors-validation` | — | — | — | ⬜ | mostly non-mutating |
| 15 | `e2e-gate-flip` | E+F: flip to blocking, deliberate-fail drill | — | — | — | — | ⬜ | owed: rollback drill |

**Status legend:** ⬜ todo · 🟡 in progress · 🔵 in review · 🟢 @gate (proven ×2) · 🟠 @quarantine · ✅ merged · 🔴 blocked

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
