# Plan: Harden Bruno API tests to gate staging deploys with auto-rollback

## Current status

> **Where we are (2026-05-26 ~19:00 CET):** Sections A + B + D-partial are merged on `develop`. Six PRs landed; **PR 670 is OPEN with 10 commits awaiting GitHub Actions recovery** (Actions in major outage / critical impact since 2026-05-26 ~11:00 UTC). PR 670 bundles PR 2 (events-api mechanical split) + PR 2a (event-types-api tests) + PR 2b (event-full-workflow state-machine walk + invalid transitions) + apex-domain CORS hotfix + cross-service 405 handler + dev-DB cleanup SQL + 4 per-test fixes from a full-suite local audit + plan-doc PR 11 redesign scope. **Stacked because Actions is down**; will split via cherry-pick post-merge if reviewers prefer. **Local Bruno baseline (2026-05-26 post-DB-cleanup): 11/13 collections green, 245/247 tests = 99.2%.** Only red: speaker-pool-api (17 Epic 11 redesign failures — PR 11 territory) + speaker-portal-api (4 cascade failures from same root, also PR 11-class). **Next: PR 6 batched audit-pass closeout** (see §"Next: batched audit-pass closeout (PR 6)" below) — covers steps 2 + 4 of §D across tasks-api + the 5 new collections from PR 2. Then PR 11 (~12 files, ~230 LOC redesign per ADR-009), PR 13 (PCS `partner_meetings` entityType + staging re-verify), and PR 14 (gate flip + F1 deliberate-fail exercise). All four are scoped and ready for a fresh session to pick up without re-deriving context. Out-of-band: **#669** retired the dead `staging.batbern.ch` domain (merged 2026-05-25, commit `1a6e2ce2`); **#665/#667/#668** fixed gateway URI double-encoding, CI role-token plumbing, and the Bruno runner `((var++))` set -e trap respectively. Tag-stable promotion correctly remained SKIPPED on #669 — Bruno still red, gate legitimately closed; #668's counter fix is verified-working on a red-Bruno run.

Update this one line on every PR merge so anyone (including a fresh Claude session) can pick up the work without re-reading the whole plan.

## Progress log

This plan is **not** tracked as BMad stories — it's test infrastructure work + opportunistic bugfixes. The table below is the single source of truth for progress. Each PR's last commit before merge updates its row in this table.

| PR # | Branch | Scope | Status | Merged | Findings / bugs discovered |
|------|--------|-------|--------|--------|---------------------------|
| 1 | `test-enhancement/bruno-staging-hardening-infra` | Sections A + B: cleanup endpoints, runner flags, ECR `staging-stable` tag, deployment alarms, Bruno-failure rollback job, one-time legacy-junk cleanup script (17 rows). | ✅ merged | 2026-05-25 (#664, `5265f61a`) | A3 deployment alarms deferred (false-positive rollback risk vs marginal value over circuit breaker — revisit after empirical Container Insights data). Companies `@Pattern` validation deferred — see audit-bugs section. **Discovered during impl**: (1) ResponseStatusException → 500 swallowing in EMS + PCS GlobalExceptionHandlers (fixed in 5aaff459 + 3a398e80). (2) Gateway `/api/v1/admin/*` route fell through to EMS — fixed in c36bc744 with per-service path discriminator (`/api/v1/admin/test-fixtures/{cums,ems,pcs}/cleanup`). (3) `@EnableMethodSecurity` was `@Profile("!local")` — flipped in 46c5dc75. (4) CUMS Checkstyle indent on `@ApiResponses` — fixed in 7799b2ec. (5) Bruno CI step failed `expected 401 to equal 403` on admin-cleanup-api auth-matrix tests — root cause was missing `SPEAKER_AUTH_TOKEN` / `PARTNER_AUTH_TOKEN` env vars in CI; resolved by out-of-band #667 (11.F.1 commit `bce55b12` honoring pre-set role env vars). (6) **Bruno runner aborted on first all-green run** under `set -e` due to `((passed++))` returning 0 (PRE-increment value) — resolved by out-of-band #668. |
| 2 | `test-enhancement/bruno-events-api-split` (= PR 670, 10 commits) | Section C mechanical-only: file moves + cleanup hooks for 4 active collections + 2 stubs (event-types-api, event-full-workflow-api gets seeded with tests 12/13/16). Net-new tests deferred to PR 2a + PR 2b. **+ bundled apex-domain hotfix from #669 fallout** | 🔵 in review (PR 670) — awaiting GitHub Actions recovery | — | events-api was 49/60 on staging pre-split — splitting is the unlock for PRs 6 (batched audit) + 11 + 12. **Bundled hotfix (claude-review on #669, unfixed at merge):** `RateLimitingFilter.java:155` + `TurnstileVerificationFilter.java:199` both ended up with `origin.equals("https://www.batbern.ch") \|\| origin.equals("https://www.batbern.ch")` (apex `https://batbern.ch` dropped from the OR). Fix: both filters now delegate via `corsHandler.isOriginAllowed(origin)`. |
| 2a | `test-enhancement/bruno-events-api-split` (stacked on PR 2 — Actions outage) | Net-new event-types-api tests: 9 Bruno tests (GET list + GET specific + GET invalid + idempotent PUT capture-then-replay + PUT validation 400 + POST/DELETE 404/405). **+ bundled backend hygiene fix**: `HttpRequestMethodNotSupportedException` handler in all 4 services' `GlobalExceptionHandler` (EMS, CUMS, PCS, SCS) — without it, POST/DELETE on a GET-only route returned 500 (same gotcha class as the `MethodArgumentNotValidException` rule in project-context.md). | 🔵 in review (in PR 670) | — | 19/19 tests green ×2 locally. |
| 2b | `test-enhancement/bruno-events-api-split` (stacked on PR 2/2a — Actions outage) | Net-new event-full-workflow-api tests: 7-step state-machine walk CREATED → ARCHIVED via PUT `/events/{code}/workflow/transition` with `overrideValidation:true` + 2 invalid-transition rejection tests + 3 fixture events + 2 cleanup hooks. Registrations CRUD against `REGISTRATION_OPEN` deferred to potential PR 2c — not blocking. | 🔵 in review (in PR 670) | — | 44/44 tests green ×2 locally. |
| 2c | also stacked in PR 670 — DB cleanup + 4 local-dev test fixes | `scripts/db/bruno-dev-pre-cleanup-2026-05-26.sql` one-shot: deleted 88 user_profiles + 14 companies + 3 test events (cascading 14 sessions, 12 speaker_pool, 30 registrations, 18 session_content_history, 1 newsletter_send) + 1 partner. Preserved 11 real Bruno-named people + 2 real partner_meetings. Plus 4 test fixes: users-api/15 timestamp-collision subprefix, sessions-api/21 stale presentationTitle assertion (Story 11.E.8 V100 dropped the column), speaker-portal-api/03+04 magic-login deleted endpoints now accept 401-or-404. | 🔵 in review (in PR 670) | — | Local Bruno: 11/13 collections green, 245/247 tests (99.2%). |
| 3 | `test-enhancement/bruno-audit-file-upload-api` | D.1: light audit | ✅ merged | 2026-05-25 (bundled into #666, `a05dce90`) | — |
| 4 | `test-enhancement/bruno-audit-companies-api` | D.2: light audit | ✅ merged | 2026-05-25 (bundled into #666, `a05dce90`) | — |
| 5 | `test-enhancement/bruno-audit-users-api` | D.3: light audit + F1/F2/F3/F4 fixes (see below) | ✅ merged | 2026-05-25 (bundled into #666, `a05dce90`) | F1 (var capture) + F2 (test 14 → 95-delete-test-user, regex tightened, lastName `Test`) + F3 (resolved via F2 + ordering) + F4 (CUMS `ADDITIONAL_EMAILS` entityType + 00/00a/99/99a cleanup hooks) all landed. **Surprise dependency:** F2's encoded-email DELETE returning 401 turned out to be a gateway URI double-encoding bug (`%40` → `%2540` tripping Spring's `StrictHttpFirewall`) — fixed in out-of-band #665, not strictly in PR 5 scope but blocked users-api from going green. Also: 7 `.bru` cleanup files added by PR 3/4/5 had `#` comments between `meta { }` and the next block; Bruno's parser silently drops such files. Caught + fixed in #666's `38fcbcea`; CLAUDE.md guardrail in #665. |
| 6 | `test-enhancement/bruno-audit-batched-closeout` | **Batched audit-pass closeout** — Section D steps 2 (canonical prefix normalization) + 4 (DELETE-then-GET-404) across **tasks-api + 5 collections from PR 2** (events-crud-api, event-topics-api, sessions-api, event-full-workflow-api; speaker-pool-api excluded — covered by PR 11 redesign). Scope expanded from original "tasks-api light audit" because PR 670 already shipped step 3 (00/99 hooks) for all 5 new collections, leaving steps 2 + 4 as the only audit-checklist items still owed. tasks-api also gets canonical-hook migration (its 00-create-test-event / 99-cleanup-test-event style predates PR-1 cleanup endpoint pattern). | ⬜ not started — **NEXT after PR 670 merges** | — | See "Next: batched audit-pass closeout (PR 6)" section below for concrete deliverables + per-collection action list. Estimated ~2 hours; one PR, six collections. |
| 7 | _absorbed into PR 6 (batched)_ | ~~D.5: event-types-api~~ | 🟢 absorbed | — | Step 3 (00/99 hooks) N/A — no mutable test data (capture-then-replay PUT). Step 2 + step 4 inapplicable (no DELETE endpoint exists by design). Effectively complete after PR 670 / PR 2a; row kept as historical numbering anchor. |
| 8 | _absorbed into PR 6 (batched)_ | ~~D.6: event-topics-api~~ | 🟢 absorbed | — | Step 3 shipped in PR 670. Step 2 already canonical (`bruno-test-topic-{ts}`). Step 4 will be applied for test 67 (DELETE event used by topic tests) in the batched PR. |
| 9 | _absorbed into PR 6 (batched)_ | ~~D.7: events-crud-api~~ | 🟢 absorbed | — | Step 3 shipped in PR 670. Step 2 + step 4 in the batched PR. Event prefix normalization needs investigation — current tests rely on server-generated `BATbern{N}` codes; if the create-event endpoint accepts a client-supplied `eventCode`, normalize to `BRUNO-TEST-{ts}` so the cleanup-endpoint prefix sweep is no longer a no-op. |
| 10 | _absorbed into PR 6 (batched)_ | ~~D.8: sessions-api~~ | 🟢 absorbed | — | Step 3 shipped in PR 670. The 1 pre-existing failure (`21-assign-speaker-to-session.bru` `presentationTitle` assertion) was fixed in PR 670's commit `753c1315` — Story 11.E.8 V100 dropped `session_users.presentation_title`. Step 2 + step 4 in the batched PR. |
| 11 | `test-enhancement/bruno-audit-speaker-pool-api` | D.9: ~~light audit~~ **workflow-aware redesign** (new collection from PR 2, cross-service cleanup) | ⬜ not started — **scope upgrade** | — | **Not a light audit — ~12 files touched, ~230 LOC.** Pre-Epic-11 tests in this collection use `PUT /status` to reach `READY`, which is now blocked by design per ADR-009 (READY is a provisioning gate, only reachable via `POST /promote` — Story 11.D.1). 17 failing assertions categorised in 4 buckets — see "Local pre-flight findings — 2026-05-26 (speaker-pool-api)" above. Also includes the deferred CUMS `bruno.test.` / `promote.e2e.` regex widening for cross-service user cleanup. **Also fixes speaker-portal-api's 4 cascade failures** (tests 02 send-invitation, 09 send-decline, 27 approve-content, 33 respond-cognito — same Epic 11 contract drift; same fix shape: thread tests through `POST /promote` to reach READY before invoking endpoints that require it). |
| 12 | _absorbed into PR 6 (batched)_ | ~~D.10: event-full-workflow-api~~ | 🟢 absorbed | — | Step 3 + state-machine walk + invalid-transition tests shipped in PR 670 / PR 2b. Step 2 + step 4 in the batched PR. Deferred "registrations CRUD against AGENDA_PUBLISHED" stays out of scope until a dedicated PR 2c if pursued at all (not blocking PR 14). |
| 13 | `test-enhancement/bruno-audit-partners-api` | D.11: light audit + PCS `partner_meetings` entityType extension | ⬜ not started | — | **Local Bruno: 18/18 ✓** (verified 2026-05-26 post-#669). Plan's earlier "3/18 staging" figure was pre-#669 — staging needs re-verification once PR 670 merges. **Main outstanding deliverable: extend PCS cleanup endpoint with a `partner_meetings` entityType.** `partner_meetings` is a standalone table (no FK to partners), so today's `partners` entityType doesn't reach it via cascade. Bruno's `partner-meetings-api` collection currently leaves meetings behind on every run. Local DB audit (2026-05-26) found 2 real partner_meetings rows — explicitly preserved by the dev cleanup SQL. Implementation: option 1 from plan §B2 — `meeting_id` allowlist parameter on PCS cleanup endpoint. ~50 LOC + 1 integration test. |
| 14 | `test-enhancement/bruno-gate-flip` | Section E: remove `continue-on-error`, prove rollback path | ⬜ not started — gated on **PR 670 merged + PR 6 (batched) + PR 11 + PR 13 all green ×2 on staging** | — | F1 deliberate-fail rollback exercise (workflow_dispatch with `enable_bruno_rollback: true` on a throwaway branch) is owed before the gate flip — should fire once PR 11 + PR 13 land and all collections go green on staging twice. |

**Out-of-band PRs landed during this session** (not in the original 1-14 enumeration but tightly coupled to the staging-hardening work — Bruno failures surfaced them):

| PR # | Branch | Scope | Status | Merged | Why it was needed |
|------|--------|-------|--------|--------|------------------|
| 665 | `fix/gateway-path-encoding-and-cache-control` | Gateway URI verbatim forwarding (no double-encode of `%40`) + upstream Cache-Control passthrough + CLAUDE.md Bruno `docs { }` guardrail. | ✅ merged | 2026-05-25 (`82ee9734` → `3d637915`) | Found by users-api/17 returning 401 on encoded-email DELETE (StrictHttpFirewall rejecting `%2540`) and users-api/20 failing Cache-Control assertion (gateway stripping upstream `public, max-age=86400`). Both blocked PR 5 from going green. |
| 668 | `fix/bruno-runner-counter-set-e-trap` | Replace `((var++))` with `var=$((var+1))` in Bruno runner counters. | ✅ merged | 2026-05-25 (`62e77a13`) | After 11.F.1 fixed the role-token plumbing and admin-cleanup-api went 9/9 green for the first time, the runner aborted on `((passed++))` returning 0 under `set -e` BEFORE the summary block. GitHub recorded `outcome=failure`, `tag-stable-on-success` skipped, staging-stable never promoted. Invisible while admin-cleanup-api had real failures. |
| 669 | `fix/remove-dead-staging-batbern-ch-domain` | Retire dead `staging.batbern.ch` / `api.staging.batbern.ch` / `cdn.staging.batbern.ch` domains from test scaffolding (73 files). | ✅ merged | 2026-05-25 (`1a6e2ce2`) | Bruno's `collection.bru:10` set `Origin: https://staging.batbern.ch` for every request; the deployed gateway correctly rejected the defunct origin → 14 `should have CORS headers` assertions failed on staging (7 file-upload + 7 companies). Substitutes to `www.batbern.ch` / `api.batbern.ch` / `cdn.batbern.ch`; dedupes the resulting duplicate entries in gateway CORS allow-lists and CSP. **Two apex-domain regressions slipped past auto-merge** (claude-review flagged them; auto-merge fired anyway because PR-review-count is 0): `RateLimitingFilter.java:155` + `TurnstileVerificationFilter.java:199` ended up with `origin.equals("https://www.batbern.ch") \|\| origin.equals("https://www.batbern.ch")` instead of `... \|\| origin.equals("https://batbern.ch")`. Browsers hitting the apex pass CORS (CorsHandler is correct) but get rejected by rate-limit + Turnstile filters. Hotfix bundled into PR 2. Minor: duplicate Swagger UI server entry (staging label points to same URL as production) not fixed — acceptable wording-stale post-consolidation. |

Plus PR **#667** (`feature/11-f-1-magic-link-teardown`, Epic 11.F.1) merged 2026-05-25 (`27a990ca`) — separate epic but contained commit `bce55b12 fix(ci): honor pre-set role env vars in run-bruno-tests.sh` which unblocked the role-token plumbing in CI.

**Status legend:** ⬜ not started · 🟡 in progress · 🔵 in review · ✅ merged · 🔴 blocked

**PR description convention:** every PR includes `Refs: docs/plans/bruno-staging-hardening.md PR #<n>` and the final commit updates the row above (status, merged date, notable findings/bugs). When real bugs are discovered during a test audit, file them as separate fix-commits in the same PR and note the bug in "Findings / bugs discovered."

## Pre-PR-1 audit results — 2026-05-24

Query executed against staging RDS (`batbern-staging-postgres.c7qauya0ie7a.eu-central-1.rds.amazonaws.com`, db `batbern`) via SSM tunnel. Query: `/tmp/bruno-audit-query.sql`. Results: `/tmp/bruno-audit-results.log` + `/tmp/bruno-audit-extra.log`.

### Critical finding — naming-collision near-miss
**12 real users named "Bruno"** (`bruno.blumenthal@ruag.com`, `bruno.frey@astra.admin.ch`, `bruno.linder@sbb.ch`, etc.) match `LIKE 'bruno%'`. A naïve prefix-cleanup would have deleted real people. The dot-anchored regex `^bruno\.test\.[0-9]+$` from B1 is the only safe pattern — confirmed binding, not optional. The same applies to companies: `^[A-Za-z0-9]+$` OpenAPI pattern is **not enforced server-side** (confirmed below: company names with spaces and 30 chars persist) — so the cleanup-prefix regex must do all the safety work.

### Pre-existing test junk to remove on PR 1 deploy

| Table | Identifier | Count | Examples | Disposition |
|-------|-----------|-------|----------|-------------|
| `companies` | `name` | 1 | `bruno test company 1761143046` (2025-10-22) | Disposable — Bruno residue |
| `companies` | `name` | 6 | `E2E Test Company 1768749259021` … (Jan–Feb 2026) | Disposable — Playwright E2E residue |
| `companies` | `name` | 3 | `testag`, `testcompanya`, `testdeclinea` (Jan–Mar 2026) | Disposable — automated fixture residue |
| `partners` | `company_name` | 7 | `brtest117`, `brtest150`, `brtest288`, `brtest424`, `brtest675`, `brtest861`, `brtest899` | Disposable — current Bruno partner test residue (44% of partners table!) |
| `user_profiles` | `username` | 1 | `user.eetest@batbern-test.ch` | **KEEP — still in use** (user confirmed 2026-05-24). Exclude from cleanup script and from cleanup-endpoint regex match list. |
| `events` | `event_code` | 0 | — | Clean |
| `sessions` | `session_slug` | 0 | (1 real session about Test Automation at SBB matches `%test%` — KEEP) | Clean |
| `topics` | `topic_code` | 0 | — | Clean |
| `registrations` | `registration_code` | 0 | — | Clean |
| `logos` | `s3_key` | 0 | — | Clean (4 PENDING auto-purge in 24h) |
| `event_tasks` | (joined via `events`) | 0 | — | Clean |
| `speaker_pool` | (joined via `events`) | 0 | — | Clean |
| `user_profiles` (`email='%@e2e.batbern.invalid'`) | reserved test domain | 0 | — | Clean — reservation works |

**Total disposable rows: 17** (10 companies + 7 partners; `user.eetest` excluded — still in use). Partner cascade likely brings additional rows in `partner_meeting_*`, `topic_votes`, `partner_notes` etc. for the 7 `brtest*` partners — to be counted by the PR 1 cleanup script as a SELECT-then-DELETE before any destructive operation.

### Bugs confirmed by audit — dispositions

- **`companies.name` server-side validation NOT enforcing the OpenAPI pattern `^[A-Za-z0-9]+$`** — **but the deeper finding is that the OpenAPI spec is wrong, not the validation.** Initial reading suggested adding `@Pattern` enforcement to the DTO. Closer look at `services/company-user-management-service/src/test/java/.../integration/CompanyControllerIntegrationTest.java:62,84` reveals the existing integration test fixture uses `"Test Company"` and `"New Company"` **with spaces** and the tests pass. `CreateCompanyRequest.java` DTO has only `@Size(min=2, max=255)`. The DTO documentation example value is `"Swisscom AG"` (also with space). Production data shows 7+ rows with spaces are clearly real test/E2E residue but the broader pattern of "company name with space" is the documented intent of the code. **Conclusion: the OpenAPI spec's `^[A-Za-z0-9]+$` pattern was aspirational and doesn't reflect reality.** Adding `@Pattern` enforcement would break the existing test suite plus reject most legitimate Swiss company names. **Revised decision (2026-05-24): defer to a separate follow-up ticket.** That ticket needs a product/architecture call: either (a) relax the OpenAPI spec to match code reality, allowing spaces/periods/hyphens/ampersands, OR (b) tighten code + tests + data to enforce strict alphanumeric per the spec, which is a much bigger change. Neither belongs in PR 1.
- **`partners.company_name VARCHAR(12)` constraint confirmed enforced** — all 7 `brtest*` entries are ≤12 chars. Still the known mismatch with `companies.name VARCHAR(255)` from risk #7.1. **Decision (user, 2026-05-24): separate Linear ticket, not in PR 1.** Requires a migration to widen the column; out of scope for test infrastructure work.

### Cleanup approach for the historical junk — decided

**Decision (user, 2026-05-24): one-time SQL cleanup script in PR 1.** Targets the 17 specific rows by exact-match list (10 companies + 7 partners), not by regex. The cleanup endpoint's canonical regexes (B1) won't match these legacy patterns (`bruno test company 1761143046` has spaces; `E2E Test Company` is mixed case with spaces; `testag/testcompanya/testdeclinea` follow no canonical), and that's fine — historical patterns aren't worth supporting in the long-running endpoint.

PR 1 ships:
1. The cleanup endpoint (for ongoing canonical patterns going forward).
2. A one-shot SQL migration `V<n>__bruno_staging_pre_cleanup.sql` (or a dedicated `scripts/db/bruno-staging-pre-cleanup.sql`) that exact-match deletes the 17 rows + their cascade dependents (partner_meeting_*, topic_votes, partner_notes for the 7 partners). SELECT-then-DELETE pattern: SELECT first to log counts, then DELETE inside a transaction with a final COUNT verification.
3. Audit log entries in CloudWatch for each deletion.

The script runs exactly once when PR 1 deploys. Never re-runs.

## Local pre-flight findings — 2026-05-25 (users-api)

> **Status (2026-05-25 end-of-session): ✅ ALL FOUR FINDINGS RESOLVED.** F1, F2, F3, F4 fixes shipped in PR #666 (`a05dce90`) — see PR 5 row in the progress table. F2's encoded-URL 401 sub-cause turned out to be a separate gateway bug (URI double-encoding) and was fixed in out-of-band #665. users-api now reports 35/35 ✓ on staging post-#666 merge (verified). The detailed analysis below is retained as historical context for anyone debugging similar patterns in PR 2's split events-api collections or PR 13's partners-api.

Ran `./scripts/ci/run-bruno-tests.sh development --collection users-api` after fix-commit `74b8cd8a` (which moved 3 `#`-prefixed comment blocks into `docs { ... }` blocks so Bruno's parser would stop skipping the files). Parser warnings are gone — but the now-parseable files surfaced 3 genuine test failures, plus the run surfaced 2 other pre-existing failures that the previously-skipped files were masking. **Result: 31 requests, 28 passed, 3 failed.** Full log: `/tmp/bruno-users-api.log` (locally — not committed).

These belong to PR 5 (`test-enhancement/bruno-audit-users-api`, D.3) and the auditor for that PR should fix them as part of the light audit pass.

### F1 — `users-api/18-add-additional-email-duplicate.bru` returns 400 instead of 409 (exposed by `74b8cd8a`)

Request body sent literally as `{"email": "{{authUserEmail}}", ...}` — Bruno is not interpolating `authUserEmail` because it's never defined. `bruno-tests/environments/development.bru:1-16` has no `authUserEmail` var; `grep -rn "authUserEmail" bruno-tests/` returns only the consumer site. The server rejects the literal `{{...}}` string with `Validation failed: email - must be a well-formed email address` → 400, instead of exercising the 409 duplicate-error branch the test intends.

**Why new:** before `74b8cd8a`, the parser skipped this file entirely. The docs-block fix made it parseable, which exposed the latent var gap. The test was effectively dead code.

**Fix options for the auditor (cheapest first):**
- Capture email from `01-get-current-user` via a `script:post-response { bru.setEnvVar("authUserEmail", res.getBody().email); }` and let `18-` consume it. Lines added: 1 in `01-`, 0 in `18-`.
- OR inline a known organizer email directly in the body (couples test to env's auth user — brittle but simplest).
- OR add `authUserEmail: {{process.env.AUTH_USER_EMAIL}}` to all three env files plus the corresponding GitHub secret (heaviest).

Recommended: the post-response capture in `01-`. Same pattern is already used elsewhere in the collection (`01-` stores `firstName`/`lastName`/`bio` to env vars for `03b-restore-current-user`).

### F2 — `users-api/17-delete-additional-email.bru` returns 401 instead of 204 (pre-existing)

DELETE `/users/me/additional-emails/{{encodedAdditionalEmail}}` returns 401. The pre-request precondition check passed (so `additionalEmail` env var was set by test 15). The request is unauthenticated at the server.

**Root cause:** test-ordering bug. Bruno runs files alphanumerically. `14-delete-test-user.bru` (`bruno-tests/users-api/14-delete-test-user.bru:8`) DELETEs `{{testUsername}}` which `development.bru:8` defines as `batbern.organizer` — the **current auth user**. `14` returns 204 (assertion `res.status: in [204, 403, 404]` accepts all three). Tests 15/16 still pass because PreTokenGen + Pattern 3b silently re-hydrate the user on the next authed call (see `docs/architecture/06b-user-lifecycle-sync.md` §"Pattern 3b"). But by the time `17-` (DELETE on encoded-email path) runs, the user's `user_additional_emails` row is gone (cascade from the user delete in `14-`), or auth-state is inconsistent enough that the gateway returns 401.

**Independent of PR 1's docs-block fix.** This test has been broken for a while; nobody noticed because runs against staging silently no-op'd (A1's broken baseUrl).

**Fix options for the auditor:**
- Point `14-delete-test-user.bru:8` at a disposable username (e.g. `bruno.test.<ts>` created by `04-create-user.bru` earlier in the run) instead of `{{testUsername}} = batbern.organizer`. **This is the right fix** — `14-` is clearly meant to exercise GDPR-delete, not to nuke the auth user.
- OR rename `14-` to `99-delete-test-user.bru` so it runs after every other test in the collection. Quick but doesn't fix the underlying "tests should not destroy their own auth user" problem.

This also implies updating `B1`'s canonical-naming rules to make explicit that `testUsername` in `development.bru` must NEVER be the same user whose JWT is in `AUTH_TOKEN` — a footgun worth pinning down before PR 1 ships its 3 cleanup endpoints.

### F3 — `users-api/20-public-user-by-username.bru` returns 404 instead of 200 (pre-existing, cascades from F2)

Anonymous `GET /public/users/batbern.organizer` returns 404 from CUMS. Same root cause: `14-delete-test-user.bru` deleted `batbern.organizer` earlier in the run, and the public projection endpoint legitimately can't find them. The 404 is correct given the state — the test is wrong about the precondition.

**Fix:** falls out automatically once F2 is fixed (auth user no longer being deleted mid-run). No separate change needed.

### F4 — `users-api/15-add-additional-email.bru` accumulates rows; 6th run+ blocks at 422 (discovered 2026-05-25, second run)

Re-running the collection after the first round of findings surfaced a new failure mode on test 15:

```
status: 422
errorCode: ADDITIONAL_EMAIL_LIMIT_REACHED
message: "Additional email limit reached: maximum 5 per user"
```

Local DB snapshot confirms: `batbern.organizer` (the auth user, since `development.bru:3 authToken: {{process.env.AUTH_TOKEN}}` resolves to that identity) has 5 leftover rows in `user_additional_emails` matching `bruno-additional-%@example.com`, dating from earlier today's runs.

**Root cause is a pairing-without-isolation bug**, not a state-cleanup miss in the cleanup endpoint:
- `15-add-additional-email.bru` POSTs `bruno-additional-{{$randomInt}}@example.com` to `/users/me/additional-emails` (against the *auth* user, not a disposable test user).
- `17-delete-additional-email.bru` is supposed to delete it, but has been failing for the F2 reasons (auth user deleted mid-run by test 14, encoded-URL 401, precondition-trip when 15 itself fails).
- Each leaked row stays forever. After 5 leaks the cap is hit and every future test 15 immediately 422s before it can even try to write — making the leak permanent until a manual `DELETE`.

**No single commit caused this.** It's an accumulation pattern: Story 10.32 (`9c35bf11`) introduced both the cap and the test, but the test "worked" as long as 15→17 always paired up cleanly. Recent PR-1 changes that exposed the previously-skipped tests (my docs-block fix at `74b8cd8a`) and changes that altered the auth user's behavior (`46c5dc75` flipping method security on in local; the staging ORGANIZER role re-grant earlier this session making test 14 actually destructive) all *raised the failure rate* on test 17, accelerating the leak — but the leak vector existed already.

**Fix for the PR 5 auditor — three pieces, do all three:**

1. **Extend the CUMS `TestFixtureCleanupController` with an `ADDITIONAL_EMAILS` entityType.** Same shape as the partner_meetings TODO in PR 13. Prefix regex along the lines of `^bruno-(test|additional)-[0-9]+@example\.com$` (or normalize to canonical `^bruno-test-[0-9]+@e2e\.batbern\.invalid$` and migrate the test). Single DELETE on `user_additional_emails` by `LOWER(email) LIKE LOWER(?) || '%'`. Add the regex to the controller's bound `Map<CleanupEntityType, Pattern>` per B2's controller-layer enforcement. ~50 lines + 1 integration test.
2. **Normalize the test prefix to a canonical pattern per B1.** Today's `bruno-additional-{{$randomInt}}@example.com` doesn't match any canonical regex from B1's table — it's a one-off prefix invented inside this test file. Either (a) widen B1 to recognize `bruno-additional-` as a second canonical prefix for additional-email fixtures, OR (b) change the test to `bruno-test-{{$timestamp}}@e2e.batbern.invalid` and reuse the existing email canonical. (b) is preferable — it consolidates one less regex.
3. **Add `00-pretest-cleanup.bru` + `99-posttest-cleanup.bru` to `users-api/`** per B3. Both POST to the cleanup endpoint with `entityType=ADDITIONAL_EMAILS` + the canonical prefix. Status assertion `oneOf([200, 204, 404])` so they never fail the run. This breaks the create/delete coupling entirely — even if test 17 fails, the next run starts clean.

**One-time local-DB unblock before PR 5 can run green locally:**

```sql
DELETE FROM user_additional_emails
WHERE email LIKE 'bruno-additional-%@example.com';
```

**Same shape as PR 13's TODO.** The two share a structural pattern: an entity that lives on a *real* parent (additional_emails on real users; partner_meetings as a standalone table) and that the existing cleanup entityTypes can't sweep because they only delete the parent or only follow FK cascades from a test-prefixed parent. Worth pulling out as a B2 framing note: **"cleanup endpoint entityTypes must enumerate every table that tests write to, not just the entity owners."**

### Cross-cutting note for the PR 5 auditor

The four failures together demonstrate a class of test-design bug this plan should explicitly track: **implicit dependencies on test order + shared mutable state in dev DB**. Section D.5 already mandates "run twice" — but that catches *idempotency* bugs, not *self-destruction* bugs like `14-` deleting its own auth user, or *accumulation* bugs like `15-` leaking rows to a real user every time `17-` fails. Three adjustments worth considering for the D template:

1. **Add a `00-` pre-flight that asserts the auth user exists** — fail-fast if a previous run left the user deleted, instead of cascading into 5 confusing failures.
2. **Forbid tests that mutate `{{testUsername}}` when it equals the auth user.** Either route them through a disposable `bruno.test.<ts>` created earlier in the run, OR move them to the final 99-block.
3. **Forbid tests that write rows to a *real* user's tables (e.g. additional emails, profile pictures) without an unconditional posttest cleanup.** Pair every such create with a `99-` posttest sweep OR rewrite to use a disposable test user.

Neither (1) nor (2) is mandatory for PR 5 — they're nice-to-have hardening for D. (3) is now binding for PR 5 specifically because F4 demonstrates the exact failure mode it would have prevented. Captured here so the PR 5 auditor (or whoever later notices similar patterns in another collection) doesn't have to re-derive it.

## Local pre-flight findings — 2026-05-26 (speaker-pool-api) — PR 11 redesign scope

> **Status (2026-05-26):** Discovered during PR 2 local verification — `speaker-pool-api` lands at **61/78 tests** locally (17 failing). The failures are not test-infrastructure bugs; they're the consequence of the **Unified Speaker Workflow refactor (Epic 11 / ADR-009)** that landed across PRs 11.B.1 → 11.F.1 and changed the API contract underneath these tests. The tests were written against the old Story 5.4 + 6.0a contract and have been silently red on staging since Epic 11 merged. PR 11's "light audit" framing in §D is therefore **insufficient for speaker-pool-api specifically** — it needs a workflow-aware redesign of the 35–66 test chain. Estimated effort: 2–3× a normal audit-pass PR.

### Root cause — the new state machine (ADR-009 §0.1)

The legacy workflow accessed via `PUT /events/{code}/speakers/{id}/status` looked like:

```
IDENTIFIED → CONTACTED → READY → ACCEPTED → (CONTENT_SUBMITTED → QUALITY_REVIEWED)
                                 ↘ OVERFLOW (excess speakers)
                                 ↘ WITHDREW (dropouts)
```

ADR-009 replaces it with:

```
IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED
                       ↑                                                              ↓
                       │                                                              ↓
            POST /promote                                              (any state) → DECLINED
            (User provisioning gate)
```

Key changes that break the existing tests:

1. **`READY` is now a provisioning gate.** Transition INTO `READY` is unreachable via `PUT /status` — only via `POST /api/v1/events/{code}/speakers/{speakerId}/promote` (Story 11.D.1). The PUT endpoint explicitly throws `ReadyRequiresPromoteException` → 400 with code `READY_REQUIRES_PROMOTE_ENDPOINT`. See `services/event-management-service/src/main/java/ch/batbern/events/exception/ReadyRequiresPromoteException.java`.
2. **`INVITED` is a new state between `READY` and `ACCEPTED`.** Formal invitation step. `READY → ACCEPTED` directly is no longer a valid transition.
3. **`OVERFLOW` is removed.** Slot capacity is enforced at the `READY → INVITED` gate (ADR-009 §0.7) — `count(ACCEPTED) + count(INVITED) >= max_slots` blocks the transition. Excess speakers stay in `READY` indefinitely.
4. **`WITHDREW` is removed.** Replaced by `DECLINED`, the single terminal state, reachable from any non-terminal state.

### Failure categorisation

The 17 failing assertions in `speaker-pool-api` fall into four categories:

| # | Category | Example test(s) | Why it fails post-Epic-11 | Fix for PR 11 |
|---|----------|-----------------|--------------------------|---------------|
| A | PUT /status → READY blocked by design | `38-update-speaker-status-to-ready.bru` | Returns 400 with `READY_REQUIRES_PROMOTE_ENDPOINT` instead of the old 200. Test asserts `currentStatus: READY`, gets `undefined`. | Delete the test OR rewrite to assert the 400 + error code (documents the design constraint). The promote path is already covered by tests 45–47. |
| B | Chained tests assume sequential PUT advancement | `39-update-speaker-status-to-accepted` (and 41 / 42 which read history) | Speaker is stuck at CONTACTED because test 38 failed. PUT CONTACTED → ACCEPTED is invalid (must traverse READY → INVITED → ACCEPTED). | Restructure the 35–42 chain to use the modern path: PUT to CONTACTED, then `POST /promote` to READY, then PUT INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED. |
| C | Slot capacity tests assume OVERFLOW | `53-speaker-workflow-slot-capacity-409`, `54-send-invitation-slot-capacity-409` | OVERFLOW state was removed. The new gate fires at READY → INVITED. Tests may be asserting the wrong transition point or the wrong exception class. | Verify the test exercises the capacity check at READY → INVITED and that the error code matches the current `SlotCapacityReachedException` shape. |
| D | Workflow exception messages drifted | Tests asserting `'Invalid state transition for speaker …' to include 'ACCEPTED'` | The state machine emits different messages now. Asserting on text is brittle. | Switch assertions to error codes (machine-readable) rather than message text. |

### Estimated rework

| Action | Test files | LOC |
|--------|------------|-----|
| Delete or rewrite category-A test (PUT → READY) | 1 (`38-`) | ~20 |
| Restructure 35–42 to thread through `POST /promote` | 8 (`35-` through `42-`) | ~150 |
| Verify slot-capacity tests against new gate | 2 (`53-`, `54-`) | ~30 |
| Update message-based assertions to code-based | 2–3 (across category D) | ~30 |
| **Total redesign** | **~12 files touched** | **~230 LOC** |

This is 2–3× a normal audit-pass PR. PR 11 should be planned with that in mind — and a heads-up to whoever picks it up that this is a redesign, not a tune-up.

### Recommended PR 11 sequencing

1. **Fix the fixture chain first** — currently 35 (add to pool) → 36 (list) → 37 (PUT CONTACTED) is solid. 38 is the breakage point. Build forward from 37 by inserting a new `38-promote-speaker-to-ready.bru` that calls `POST /promote` (replacing the broken PUT /status pattern).
2. **Renumber from there** — old 38 becomes 38a (deleted) or 38b (rewritten as a negative test asserting the 400).
3. **Insert new invitation step** — between READY and ACCEPTED add `39-invite-speaker.bru` that calls the appropriate state-transition endpoint. Old test 39 (PUT to ACCEPTED) becomes 40, asserting INVITED → ACCEPTED instead of READY → ACCEPTED.
4. **Verify slot capacity at the new gate** — old tests 53 / 54 already point at slot capacity but may exercise the wrong transition; verify and adjust.
5. **Run locally green ×2** per Section D.5, then PR.

The 11.D.1 + 11.B.2 references in the test names indicate parts of this work were already started during Epic 11 — but only the `POST /promote` happy-path tests (45–47) were added; the legacy `PUT /status` chain wasn't pruned. PR 11 closes that loop.

## Next: batched audit-pass closeout (PR 6) — for the fresh-session pickup

> **Status (2026-05-26):** scoped, ready to start. Estimated ~2 hours. Six collections in one PR. Branch name: `test-enhancement/bruno-audit-batched-closeout`. Off `develop` AFTER PR 670 merges (do not stack on PR 670 — wait for clean baseline). All file paths and Story IDs in this section are post-PR-670 layout.

### Why one batched PR instead of 5 separate ones

The original §D rollout had one audit-pass PR per collection (PRs 6, 7, 8, 9, 10, 12). PR 670 ended up shipping the heaviest item from each (step 3: canonical `00-pretest-cleanup.bru` + `99-posttest-cleanup.bru` hooks) plus fixture-event setup where needed. That left only steps 2 + 4 from the §D checklist as actual audit work — and those are 1–5-line edits per collection. Six tiny PRs would be ceremony; one batched PR is cleaner.

speaker-pool-api is NOT in this batch — it's PR 11's redesign. speaker-portal-api isn't in the original §D rollout (dev-only) and its 4 cascade failures are the same Epic 11 root cause as PR 11, so PR 11 fixes them too.

### Per-collection action list

**tasks-api** (was PR 6; this is the only collection that genuinely needs new hook files):
1. Replace `00-create-test-event.bru` + `99-cleanup-test-event.bru` with canonical `00-pretest-cleanup.bru` + `99-posttest-cleanup.bru` calling `POST {{baseUrl}}/admin/test-fixtures/ems/cleanup` with `entityType=events, prefix=BRUNO-TEST-` (status assertion `oneOf([200, 204, 404])` per §B3).
2. Add `00b-fixture-event.bru` + `98-delete-fixture-event.bru` mirroring PR 670's sessions-api pattern.
3. **Audit-step 2** — normalize fixture event_code to `BRUNO-TEST-{ts}` if the create-event endpoint accepts a client-supplied `eventCode` field (investigate; if not, document the gap and leave server-generated `BATbern{N}`).
4. **Audit-step 4** — for any DELETE-tests, add an immediate GET that asserts 404.

**events-crud-api** (was PR 9):
1. **Audit-step 2** — same investigation as tasks-api above. Today `03-create-event.bru` sends `eventNumber` only; server generates `BATbern{N}`. Determine whether the API accepts `eventCode` in the body; if yes, normalize to `BRUNO-TEST-{ts}` so the §B3 cleanup-prefix sweep stops being a no-op. If no, file as plan §"Risks to track" item and skip the normalization for events.
2. **Audit-step 4** — `29-delete-event.bru` asserts 204. Append `29a-verify-event-deleted.bru` doing `GET /events/{{createdEventCode}}` → 404.

**event-topics-api** (was PR 8):
1. **Audit-step 2** — topics already canonical (`bruno-test-topic-{ts}`) ✓. Event fixture (`30a-`) uses the same `BATbern{N}` shape — see the events-crud item above.
2. **Audit-step 4** — `67-cleanup-topic-test-event.bru` does DELETE. Append `67a-verify-event-deleted.bru` → 404. Topics aren't currently deleted-then-verified — consider adding `34a-verify-topic-still-attached.bru` if the topic-selection flow leaves residue worth checking.

**sessions-api** (was PR 10):
1. **Audit-step 2** — `session_slug` is server-generated from session title? Verify in `01-create-session.bru` (search for any `slug` field in request vs response). If client can supply, normalize to `bruno-test-session-{ts}`.
2. **Audit-step 4** — `11-delete-session.bru` asserts 204. Append `11a-verify-session-deleted.bru` → 404. (Already-fixed by PR 670: test 21 `presentationTitle` stale assertion.)

**event-full-workflow-api** (was PR 12):
1. **Audit-step 2** — fixture events use `BATbern{N}` — same as events-crud item.
2. **Audit-step 4** — `97a-delete-workflow-fixture-event.bru` accepts `[204, 404, 409]`. Append `97c-verify-workflow-event-deleted.bru` → 404 (only when previous DELETE returned 204; skip when 409 because the event still exists by design).
3. **Optional out-of-scope**: registrations CRUD against an event in `REGISTRATION_OPEN` state — keep deferred to a hypothetical PR 2c. Not blocking PR 14.

### Verification

Run each collection twice locally per §D.5:

```bash
make dev-native-up  # ensure all services running (EMS may need flywayRepair on V86)
for c in tasks-api events-crud-api event-topics-api sessions-api event-full-workflow-api; do
  ./scripts/ci/run-bruno-tests.sh development --collection "$c"
  ./scripts/ci/run-bruno-tests.sh development --collection "$c"   # second run = idempotency check
done
```

Expected: all five green ×2 (pre-existing local-dev baseline post-PR-670: events-crud 28/28, event-topics 24/24, sessions 57/57, event-full-workflow 44/44; tasks-api 25/25). Don't open the PR if any drift.

### What's NOT in PR 6 scope (explicit non-goals)

- **speaker-pool-api** — PR 11 redesign (see §"Local pre-flight findings — 2026-05-26 (speaker-pool-api)" addendum).
- **speaker-portal-api** — fixes ride with PR 11 (same Epic 11 root cause).
- **partners-api** — PR 13 (PCS `partner_meetings` entityType extension).
- **The Section D step 1 "verbs vs OpenAPI" gap-documentation** — already covered by the PR 2a tests 08/09 + the existing per-collection inline comments. No separate sweep needed.
- **Cross-collection prefix normalization on event_code** — IF the API doesn't accept client-supplied eventCode, this entire item drops to a "plan §Risks to track" entry (not a PR 6 blocker).

### Definition of done for PR 6

1. ~10 small `.bru` files added (~7 verify-deleted-404 stubs + tasks-api hook rewrite).
2. Each of the 5 collections green ×2 locally.
3. Plan-doc rows for PRs 6, 7, 8, 9, 10, 12 updated to ✅ merged with this PR's SHA.
4. Section §D step 2 "Normalize prefix" item gets a finding-summary line noting which collections could and couldn't normalize event_code.
5. Open PR with `Refs: docs/plans/bruno-staging-hardening.md PR #6` per convention.

After PR 6 merges, only **PR 11 (speaker-pool redesign) + PR 13 (partner_meetings entityType) + PR 14 (gate flip + F1 deliberate-fail exercise)** remain before tag-stable-on-success can fire.

## Context

Staging deploys at `https://api.batbern.ch` (production account 188701360969 — "staging" is a CDK envName, not a separate environment) keep breaking after Epic 11 despite all CI tests being green. Root cause: the Bruno API contract tests in `bruno-tests/` are wired into `.github/workflows/deploy-staging.yml` as **post-deploy warnings only** (`continue-on-error: true` at line 1226). They don't gate the deploy, they don't trigger rollback, and `bruno-tests/environments/staging.bru` even points to a nonexistent host (`api.staging.batbern.ch` instead of `api.batbern.ch`) so most of them haven't actually executed against real staging in months.

**The existing per-entity collections are already there and mostly fine.** `file-upload-api`, `companies-api`, `users-api`, `tasks-api`, `partners-api`, `speaker-portal-api` already follow per-entity boundaries with timestamp-suffixed test data and inline cleanup tests. They need a light audit + small targeted patches — not a redesign. The one collection that genuinely needs splitting is the monolithic 67-test `events-api`, which today bundles events + topics + sessions + speakers + speaker-pool + reminders.

The actual fix is three things:
1. **New infra**: per-service cleanup endpoint, runner flags to iterate one folder at a time, Bruno-failure → rollback workflow, ECR `staging-stable` tag, fix the broken staging baseUrl.
2. **One structural change**: split `events-api` into 6 smaller collections in dependency order.
3. **Light audit pass** over every collection: standardize on canonical per-entity prefixes (see B1) where inconsistent, add a 2-file unconditional cleanup hook (`00-pretest-cleanup.bru` + `99-posttest-cleanup.bru`), tighten DELETE-then-GET-404 assertions. Most collections need 5-30 minutes of work, not a rewrite.

**Branch strategy:** Parent test-enhancement branch off `develop` (`test-enhancement/bruno-staging-hardening`), with **stacked PRs**: one infra PR, then 11 small per-entity PRs in dependency order, then a final gate-flip PR. The plan document itself lands as `docs/plans/bruno-staging-hardening.md` in the infra PR.

**Risk callout — this is production data.** Cleanup endpoint role-checked + prefix-regex-locked + audit-logged.

## Reality-check on the starting frame

Validated during exploration — important corrections:

- `circuitBreaker: { rollback: true }` **already exists** at `infrastructure/lib/constructs/domain-service-construct.ts:243-246`. It catches startup failures only — not "starts but returns 500s." Bruno fills that gap.
- `deploymentAlarms` are missing (line 299 has a circular-dep comment). Solvable.
- Existing collections **already** use timestamp suffixes (`bruno-test-{{$timestamp}}`), so they're already mostly idempotent. The gap is **inconsistent prefixes** (`bruno-test-`, `brtest`, none) and **conditional cleanup** (final `99-cleanup-*.bru` only runs if earlier tests passed). That's what the audit pass fixes.
- The legacy `deploy-production.yml:362-429` rollback job references the **decommissioned account 422940799530**. Dead code. Fix in same PR.
- `run-bruno-tests.sh:120` references a `speakers-api` collection that **doesn't exist on disk**. Silently skipped today.
- `speaker-portal-api` correctly stays dev-only — it depends on `E2ETestTokenController` which is gated by `@Profile({"dev","local","test","development"})` and is inactive in production-account services. Don't touch it.

## Branching & rollout

Parent branch: `test-enhancement/bruno-staging-hardening` off `develop`.

| # | Branch | Scope | Bruno gate state after merge |
|---|--------|-------|------------------------------|
| 1 ✅ | `test-enhancement/bruno-staging-hardening-infra` | A + B: cleanup endpoints, runner flags, ECR `staging-stable` tag, deployment alarms (deferred), Bruno-failure rollback job (still `continue-on-error: true`). One-time SQL pre-cleanup script. Plan doc lands here. | warning |
| 2 + 2a + 2b + 2c 🔵 | `test-enhancement/bruno-events-api-split` (PR 670, 10 commits) | C mechanical-only + event-types-api tests + event-full-workflow state-machine walk + apex-domain CORS hotfix + 405 cross-service handler + dev-DB cleanup SQL + 4 local-dev test fixes. **Awaiting GitHub Actions recovery (major outage as of 2026-05-26).** | warning |
| 3 + 4 + 5 ✅ | `test-enhancement/bruno-audit-{file-upload,companies,users}-api` | D.1 + D.2 + D.3 bundled into #666 with F4 fixes. | warning |
| 6 ⬜ NEXT | `test-enhancement/bruno-audit-batched-closeout` | **Batched §D steps 2 + 4 across tasks-api + 5 new collections from PR 2** (events-crud, event-topics, sessions, event-full-workflow; speaker-pool excluded — PR 11). See §"Next: batched audit-pass closeout (PR 6)" for action list. | warning |
| 7-10 + 12 | _absorbed into PR 6 (batched)_ — see those rows in the Progress log table | | warning |
| 11 ⬜ | `test-enhancement/bruno-audit-speaker-pool-api` | D.9 **redesign** per ADR-009 — ~12 files, ~230 LOC. Includes the cascade fix for speaker-portal-api's 4 Epic 11 failures. | warning |
| 13 ⬜ | `test-enhancement/bruno-audit-partners-api` | D.11 + PCS `partner_meetings` entityType extension (~50 LOC + 1 integration test). | warning |
| 14 ⬜ | `test-enhancement/bruno-gate-flip` | E: remove `continue-on-error`. F1 deliberate-fail exercise on a throwaway branch. Gated on PR 6 + 11 + 13 all green ×2 on staging. | **blocking + auto-rollback** |

## A. Infrastructure foundation (PR 1)

### A1. Fix broken staging URL (one-line change, do first)
`bruno-tests/environments/staging.bru` line 2: change `https://api.staging.batbern.ch/api/v1` → `https://api.batbern.ch/api/v1`. Until this is fixed, every "staging" Bruno run silently hits DNS-NXDOMAIN and `continue-on-error: true` swallows it. **Pair this fix with B's cleanup endpoint** so the first real staging run after the fix doesn't leave junk.

### A2. Runner enhancements (`scripts/ci/run-bruno-tests.sh`)
- `--collection <name>` flag — iterate one folder at a time (`./run-bruno-tests.sh staging --collection companies-api`).
- `--cleanup-only` mode — runs only `99-posttest-cleanup.bru` files. Belt-and-suspenders after a failed collection.
- `--no-bail` — relax `set -e` around the collection loop so a failed test inside a collection doesn't orphan that collection's cleanup file. Track failure in a flag; still exit non-zero at the end.
- Remove the dead `speakers-api` reference at line 120.

### A3. ECS deployment alarms (resolves circular-dep at `domain-service-construct.ts:299`)
Before creating the `FargateService`, compute `const alarmName = \`${envName}-${serviceName}-5xx-rate\`` (string only, no service-attribute dependency). Create:
- CloudWatch alarm on API Gateway `5XXError` filtered to this service's route, > 5% over 2 min.
- Alarm on `RunningTaskCount < desiredCount` over 3 min.

Pass `deploymentAlarms: { alarmNames: [...], rollback: true }` to the service. Skip target-group alarms (Service Connect topology, no ALBs). Don't pursue CodeDeploy blue/green — circuit breaker + alarms is the right tool.

### A4. ECR last-known-good tag strategy
Three tags per ECR repo (`batbern/staging/{service}`):
- `<sha7>` — immutable; task definitions pin to this.
- `staging-current` — what was just deployed.
- `staging-stable` — last-known-good (last Bruno-green deploy).

Rollback resolves `staging-stable` → SHA → registers a task definition with that SHA. Mutable tags alone don't trigger ECS redeploys; pin to SHAs.

### A5. Bruno-failure rollback job (workflow refactor)
Split monolithic `deploy-staging.yml` into 4 jobs:
1. `deploy` (current CDK steps).
2. `bruno-tests` (needs `deploy`).
3. `tag-stable-on-success` (needs `[deploy, bruno-tests]`, runs on Bruno success). Promotes `staging-current` → `staging-stable` for all 5 ECR repos.
4. `rollback-on-bruno-failure` (needs `[deploy, bruno-tests]`, runs on Bruno failure). Invokes `scripts/ci/rollback-deployment.sh staging --yes`. Posts Slack notification.

`scripts/ci/rollback-deployment.sh`: replace interactive `sleep 10` at line 33 with `[ "$2" = "--yes" ] || sleep 10`. Switch image-tag lookup from `deployments[1]` to `staging-stable` ECR resolution. Add `--dry-run`.

**While in this file, fix `deploy-production.yml:362-429`** (decommissioned account 422940799530 reference) — same shape of fix, same PR.

## B. Test data isolation + cleanup (PR 1)

### B1. Canonical naming convention — must fit real PK/format constraints

Constraints verified against Flyway migrations + JPA + OpenAPI specs. **Several of the "obvious" patterns are illegal** under these constraints — the table below is binding, not aspirational.

| Entity | Source of constraint | Constraint | Canonical pattern | Filled length |
|--------|----------------------|-----------|-------------------|---------------|
| companyName | `V3__Create_companies_schema.sql:7` + `companies-api.openapi.yml` | VARCHAR(255), `^[A-Za-z0-9]+$` (existing tests use hyphens though — verify hyphen acceptance in PR 1) | `BRUNOTESTCO<13-digit-ts>` (alphanumeric, safest) **OR** `BRUNO-TEST-CO-<ts>` if hyphens confirmed | 24 / 30 chars |
| username | `V4__Create_user_profiles_table.sql:13,59-60` | VARCHAR(100), DB regex `^[a-z]+\.[a-z]+(\.[0-9]+)?$` — **dots required, no underscores, no hyphens** | `bruno.test.<13-digit-ts>` | 24 chars |
| email | `V4__Create_user_profiles_table.sql` | VARCHAR(255), RFC 5321 | `bruno-test-<ts>@e2e.batbern.invalid` (existing convention, reserved domain) | 36 chars |
| eventCode | `V3__Add_event_code_and_organizer_username.sql:6` | VARCHAR(50), `@Size(max=50)` | `BRUNO-TEST-<13-digit-ts>` | 24 chars |
| sessionSlug | `sessions.session_slug` | VARCHAR(200) | `bruno-test-session-<ts>` | 33 chars |
| topicCode | `V24__add_topic_code_column.sql:6` | VARCHAR(255), lowercase-hyphens convention | `bruno-test-topic-<ts>` | 31 chars |
| partnerCompanyName | `V2__create_partner_coordination_schema.sql:9` ⚠️ known bug | **VARCHAR(12) — extremely tight** | `brtest<6-digit-id>` (matches existing `brtest{{$randomInt}}`) | ≤12 chars |
| filename (file-upload) | `file-upload-api.openapi.yml` | maxLength 255 | `bruno-test-<ts>.png` | 30 chars |
| registrationCode | `registrations.registration_code` | VARCHAR(100) | `BRUNO-<ts>-<6char-rand>` | ≤28 chars |

**Prefix-detection regexes** (for cleanup endpoint validation — locked in service code):
- Uppercase alphanumeric (companies): `^BRUNOTESTCO[0-9]+$` and/or `^BRUNO-TEST-[A-Z0-9-]+$`
- Lowercase-dotted (usernames): `^bruno\.test\.[0-9]+$`
- Hyphenated lowercase (sessions, topics, files): `^bruno-test-[a-z0-9-]+$`
- Hyphenated mixed-case (eventCode, registrationCode): `^BRUNO-(TEST-)?[A-Z0-9-]+$`
- Short partner: `^brtest[0-9]+$`

Cleanup endpoint accepts a single canonical prefix string per call (one per entity type), validates against the entity-specific regex above before issuing DELETE. The "single BRUNO_TEST_ prefix everywhere" idea from the previous draft is incompatible with reality — entities use different conventions; cleanup must match each.

Document the full table once in new `bruno-tests/README.md`. **PR 1 must verify whether company name hyphens are accepted server-side** (the OpenAPI pattern says alphanumeric-only but existing tests use hyphens — one of those is wrong, and we need to know which before locking the pattern).

Most existing collections already use compatible prefixes (e.g. companies-api uses `bruno-test-company-*`, partners use `brtest*`). The audit pass (D) normalizes to the canonical patterns above and updates any prefix-detection that diverges.

### B2. Per-service cleanup endpoint
**Gating (per decision: ROLE_ORGANIZER + regex prefix):**
- `POST /api/v1/admin/test-fixtures/{cums|ems|pcs}/cleanup` — one path per owning service.
  The per-service path discriminator is required because `api-gateway`'s `DomainRouter` falls
  through `/api/v1/admin/*` → EMS by default (line ~103); a single shared path would make
  CUMS and PCS cleanup endpoints unreachable via the gateway. Each path prefix is matched
  explicitly in `DomainRouter.determineTargetService` BEFORE the generic `/admin` fallback.
  Discovered 2026-05-25 during F2 (admin-cleanup-api Bruno collection) authoring.
- `@PreAuthorize("hasRole('ORGANIZER')")`.
- Body specifies `entityType` (companies | users | events | sessions | topics | tasks | partners | registrations | speaker_pool | uploads) and `prefix`.
- **Server-side constant `Map<EntityType, Pattern>`** validates the prefix against the entity-specific regex from B1 — request body cannot supply the pattern, only the literal prefix value to match. Reject anything that doesn't match the bound pattern for the given entity type. This prevents `prefix=*` / `prefix=BAT` / SQL-injection-shaped inputs at the controller layer.
- Audit-log every invocation (caller username, entityType, prefix, deletion counts).
- Per-service (not gateway fan-out) for blast-radius control.

**Cleanup order inside the endpoint** (respects FK constraints):
1. registrations → 2. session-speakers → 3. sessions → 4. speaker-pool entries → 5. event-topics → 6. tasks → 7. events → 8. topics → 9. partner-topic-votes/contacts/meetings → 10. partners → 11. users (matching `bruno.test.*`) → 12. companies → 13. file uploads (S3 + DB metadata).

**Event state machine:** dedicated `TestFixtureRepository.hardDeleteByPrefix(...)` issues raw `DELETE FROM events WHERE event_code LIKE 'BRUNO-TEST-%'` with cascade. Bypasses state guards, only reachable from this endpoint.

**Startup invariant check:** at boot, count real entities matching the canonical patterns. Log `WARN` if > 0 — don't fail-fast (might be junk from a previous failed run that this cleanup will sweep), but operators should see it.

**Deferred — partner_meetings cleanup coverage** (discovered 2026-05-25, address in PR 13 or earlier):

PCS owns `partner_meetings` but the table has no FK to `partners` — meetings are standalone events that link to partners only via `partner_meeting_attendance` / `partner_meeting_rsvps` (which cascade from `partners`, not from the meeting itself). The PR 1 PCS cleanup endpoint only handles `entityType=partners`, so:

- Deleting a `brtest*` partner removes its attendance + RSVP rows but **leaves the underlying `partner_meetings` rows in place**.
- Bruno's `partner-meetings-api` collection creates ad-hoc meetings (`meeting_type='ad_hoc'`, `scheduled_date=…`) with no Bruno-identifying column — there's no `bruno-test-` prefix on a meeting row to match against.

Options for PR 13 (partners-api audit) or a sibling PR (partner-meetings-api audit, currently not in the rollout — would need to be added):

1. **Add a `meeting_id` allowlist parameter** to the cleanup endpoint — Bruno collects IDs from create-meeting tests via `bru.setVar` and posts them back to cleanup. Tighter than a prefix; safe even though meetings lack a Bruno-prefix column. Recommended.
2. **Add a marker column** to `partner_meetings` (e.g. `created_by_test_fixture BOOLEAN`) — schema change, larger blast radius, only do this if the allowlist proves too clunky.
3. **Constrain by `created_at` window** — Bruno cleanup deletes meetings created in the last N minutes by the cleanup caller. Risky on a shared staging account (could nuke a concurrent organizer's meeting).

Recommendation: option 1, scoped to a new `meetings` entityType on the PCS cleanup endpoint. Not blocking PR 1 because the staging audit (2026-05-24) found 0 stray partner_meetings rows — Bruno meetings-api isn't run in CI today against staging (deferred to its first audit).

### B3. Per-collection cleanup `.bru` files
Each collection gets:
- `00-pretest-cleanup.bru` — POST to cleanup endpoint with that entity's prefix. Status assertion `oneOf([200, 204, 404])` — never fails the run.
- `99-posttest-cleanup.bru` — same.

Bruno orders by filename → `00-` first, `99-` last. Two-layer defense: `run-bruno-tests.sh` ALSO invokes `--cleanup-only` after each collection regardless of pass/fail.

## C. events-api decomposition proposal (PR 2)

The only collection that genuinely needs splitting. The pre-PR-2 `bruno-tests/events-api/` was 60 active tests (+ 3 disabled) covering 6 conceptually-distinct entity domains. Proposed split:

| New collection | Source tests | New tests needed | Auth |
|----------------|--------------|------------------|------|
| `event-types-api` | (none — currently untested) | All new: GET /types, idempotent PUT. **No create/delete** — API gap, not a test bug. Document the gap. | organizer |
| `event-topics-api` | 30, 30a, 31, 32, 33, 34, 67 | 00-/99- cleanup hooks. 30a (fixture event) becomes `00a-fixture-event.bru` | organizer |
| `events-crud-api` | 01, 03–07, 19, 29 | 00-/99- cleanup, DELETE-then-GET-404 | organizer |
| `sessions-api` | 08–11, 20–28 | 00-/99- cleanup, fixture event setup | organizer |
| `speaker-pool-api` | 35–56, 60–66 | 00-/99- cleanup. **Promote tests (43–48) provision real Users in CUMS** — cleanup must wipe `bruno.test.*` users via CUMS endpoint (cross-service) | organizer |
| `event-full-workflow-api` | 12, 13, 16 | Net-new tests advancing through all 8 states: CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED + registrations CRUD | organizer |

`speaker-portal-api` stays unchanged and dev-only. After the split, `bruno-tests/events-api/` directory ceases to exist (no overlap with the new collections).

### PR 2 actual scope — mechanical-only (decided 2026-05-26)

PR 2 ships **only the mechanical move + cleanup hooks**, not the net-new tests for `event-types-api` and `event-full-workflow-api`. Two follow-up PRs land the net-new content:

| Sub-PR | Scope | Status |
|--------|-------|--------|
| PR 2 (this one) | Move all 60 active `.bru` files + 3 `.disabled` files into 4 real collections (events-crud, event-topics, sessions, speaker-pool). Seed event-full-workflow-api with tests 12/13/16 (+ 13a.disabled). Add `00-pretest-cleanup.bru` + `99-posttest-cleanup.bru` (plus `00a-`/`99a-` topic sweeps for event-topics-api) for all 5 active collections. Create empty `event-types-api/` (README only — no .bru files yet). Update runner's `collections=()` array. Remove `bruno-tests/events-api/`. | 🟡 in progress |
| PR 2a | event-types-api net-new tests: GET /event-types + idempotent PUT + documenting the absent POST/DELETE as 405 or contract-absent. ~3–5 tests. | ⬜ not started |
| PR 2b | event-full-workflow-api net-new tests: advance through all 8 workflow states + registrations CRUD against a published event. ~10+ tests, longest collection. | ⬜ not started |

**Why the split:** writing the 8-state advancement test set is its own design problem (state-machine ordering, valid-transition matrix, invalid-transition rejections, post-publish-only registration windows) — pulling it out of PR 2 lets the mechanical move land fast and unblock PRs 7–12 audit work on the 4 collections that did get full content.

The `speaker-pool-api` collection is the hairy one — its cleanup spans events service (pool entries) + CUMS (provisioned Users). PR 2 ships only the events-side hook (BRUNO-TEST-* cascade clears speaker_pool); the CUMS side is gated on a small backend extension that's now a documented TODO in `speaker-pool-api/99-posttest-cleanup.bru`'s docs block. Two paths for PR 11 (D.9 speaker-pool audit) — widen CUMS USERS regex to accept `promote.e2e.`, OR change tests 45–47 to use `firstName=Bruno, lastName=Test` so the auto-generated username matches the existing `bruno.test.` prefix. Either is ~5–10 LOC.

## D. Light audit pass on each collection (PRs 3–13)

**Most collections are already structurally fine — this is a tune-up, not a rewrite.** Per-collection checklist:

1. **Glance through verbs vs OpenAPI** — note any missing CRUD operations as known gaps (e.g., event-types has no DELETE). Don't try to add backend endpoints in this scope.
2. **Normalize prefix** — search-and-replace `bruno-test-` / `brtest` / inconsistent variants → canonical patterns per B1. Usually 1–5 line changes per collection.
3. **Add `00-pretest-cleanup.bru` + `99-posttest-cleanup.bru`** calling the cleanup endpoint with that entity's prefix.
4. **Audit DELETE assertions** — every DELETE test followed by a GET that asserts 404. Today most just trust the 204.
5. **Run twice** — `./scripts/ci/run-bruno-tests.sh staging --collection <name>` twice in a row. First run cleans + creates + cleans. Second run starts dirty (intentionally — to verify pretest-cleanup is real), then green. If green ×2 → commit, open PR.

**Order** (dependency-first, lowest-coupling-first):
1. `file-upload-api` — leaf, no cross-entity coupling
2. `companies-api`
3. `users-api` — depends on companies (optional companyName ref)
4. `tasks-api` — depends on events
5. `event-types-api` (from C split) — GET-mostly, light
6. `event-topics-api` (from C split)
7. `events-crud-api` (from C split)
8. `sessions-api` (from C split) — needs event + speaker
9. `speaker-pool-api` (from C split) — cross-service cleanup, highest risk
10. `event-full-workflow-api` (from C split) — longest collection
11. `partners-api` — depends on companies + users

`speaker-portal-api`: skip (dev-only, unchanged).

Expected size: 6 of these PRs probably 5-50 line diffs (existing collections are mostly fine). The 5 new-from-events-split PRs are larger.

## E. Gate flip (PR 14)

Only after every PR 2-13 has been merged AND passed staging CI green twice in a row:

1. Remove `continue-on-error: true` at `deploy-staging.yml:1226`.
2. Confirm the `rollback-on-bruno-failure` job from A5 is wired (`if: needs.bruno-tests.result == 'failure'`).
3. Run F1's deliberate-fail exercise (below) to prove the rollback path on a feature branch before merging this PR.

## F. Verification

### F1. Test the rollback path without breaking real users

Layered, increasingly real:

1. **Dry-run mode** — `scripts/ci/rollback-deployment.sh staging --dry-run` prints what it would do without calling `aws ecs update-service`. Verifies permissions, credentials, image-tag lookup.
2. **Deliberate-fail on a branch** — add one Bruno test asserting `expect(res.getStatus()).to.equal(999)`. Push branch → run `workflow_dispatch` of staging deploy from that branch → confirm: deploy succeeds → bruno-tests fails → rollback-on-bruno-failure runs → ECS task defs pin to `staging-stable` SHA → close branch and let `develop`'s normal deploy restore current. Pick a low-traffic window (BATbern ~3 events/year).
3. **Quarterly disaster drill** — separately, push a container that exits on startup; confirm circuit breaker rolls in-place within 5 min.

### F2. Verify cleanup endpoint authorization

Before PR 1 merges, exercise the endpoint in development for every entity type:
- Non-organizer JWT + any payload → 403
- Organizer + `entityType=companies` + `prefix=BRUNOTESTCO` → 200 with deletion count
- Organizer + `entityType=users` + `prefix=bruno.test.` → 200 with deletion count
- Organizer + `entityType=users` + `prefix=BRUNOTESTCO` → 400 (prefix doesn't match the username regex)
- Organizer + `entityType=companies` + `prefix=BAT` → 400 (would match real entities)
- Organizer + `entityType=companies` + `prefix=` (empty) → 400
- Organizer + `entityType=companies` + `prefix=BRUNOTESTCO'; DROP TABLE...` → 400 (regex enforcement at controller layer)
- Organizer + `entityType=foo_bar` (unknown) → 400
- Real-data tables: query rows matching each canonical regex before/after; confirm 0 unintended deletions.

Add as a new `bruno-tests/admin-cleanup-api/` collection that runs FIRST in `run-bruno-tests.sh`, so authorization regressions are caught before any test creates state to clean.

### F3. End-to-end on staging after PR 14

1. Push trivial change to `develop` → deploy-staging.yml runs.
2. Confirm `bruno-tests` job is now blocking.
3. Check ECR: `staging-stable` = previous SHA, `staging-current` = new SHA.
4. Trigger a deliberate-fail again on a branch → confirm rollback runs and Slack notification arrives.
5. Cleanup audit logs in CloudWatch show no anomalies.

## G. Local pre-flight (every PR)

`bruno-tests/environments/development.bru` already targets `http://localhost:8000/api/v1`, and `scripts/ci/run-bruno-tests.sh` accepts `development` as its env arg. **Every PR in this plan should run its Bruno tests against local dev once before pushing.** Same code, same Spring Boot config, same Postgres, same staging Cognito for JWT — local catches regex / route / config drift that Java integration tests miss (because they bypass the gateway and call the service directly), and surfaces wiring issues seconds after a save instead of minutes after a CI run.

### Coverage matrix — what local dev validates ahead of staging

| PR scope | Local-dev coverage |
|----------|--------------------|
| **B2 cleanup endpoints** (CUMS done; EMS + PCS upcoming) | ✅ Full — regex map, ROLE_ORGANIZER gate, cascade order, audit logging |
| **B3 per-collection `00-`/`99-` cleanup hooks** | ✅ Full — HTTP-level, env-agnostic |
| **F2 `admin-cleanup-api` auth matrix** | ✅ Full — develop the 8 cases locally first; same collection then runs against staging |
| **C events-api decomposition + 6 new collections** | ✅ Full — Bruno collection structure is env-agnostic |
| **D per-collection audit pass** (PRs 3-13) | ✅ Full — every D.5 "run twice" step runs locally first |
| **Cross-service cleanup order** (PCS → EMS → CUMS) | ✅ Full — end-to-end via Bruno |
| **A2 runner flags** (`--collection`, `--cleanup-only`, `--no-bail`) | ✅ Full — already exercised against `development` |
| **A5 `rollback-deployment.sh`** | 🟡 Partial — `--dry-run` verifies arg parsing + ECR tag-lookup; AWS calls skipped |
| **A3 ECS deployment alarms** | ❌ Staging-only — CloudWatch + ECS deployment controller has no local analogue |
| **A4 ECR `staging-stable` tag promotion** | ❌ Staging-only — ECR is AWS |
| **A5 `rollback-on-bruno-failure` workflow job** | ❌ Staging-only — fires only in GitHub Actions |
| **One-time `bruno-staging-pre-cleanup.sql`** | ❌ Staging-only — the 17 target rows only exist in the staging DB |
| **Real auto-rollback ECS task-def swap** | ❌ Staging-only — deferred to F1's deliberate-fail exercise |

### Prerequisite: Pattern 3b user mirroring

Staging-issued JWTs (from `~/.batbern/staging-organizer.json`) work locally because `shared-kernel/.../security/JwtRolesConverter` falls back to the local DB when `custom:role` is empty (Epic 11.E.7 Pattern 3b). **The `user_profiles` row must exist in the local DB.** If cleanup-endpoint calls return `403 Forbidden` locally but pass in staging:

```bash
./scripts/dev/sync-users-from-cognito.sh   # mirror staging users → local DB
./scripts/ci/run-bruno-tests.sh development --collection admin-cleanup-api
```

See `docs/architecture/06b-user-lifecycle-sync.md` §"Pattern 3b" for the full pattern.

### Per-PR workflow

1. Code + Java unit/integration tests green (TDD red-green-refactor).
2. **Local pre-flight**: `make dev-native-up` + the relevant `./scripts/ci/run-bruno-tests.sh development --collection <name>` run.
3. Commit + push.
4. Staging CI re-runs the same Bruno tests (still `continue-on-error: true` until PR 14).
5. After PR 14: staging Bruno is the deploy gate; local pre-flight remains the first signal.

For PR 1 specifically, the EMS and PCS cleanup-endpoint work is fully exercisable locally (Section B is ~90% of remaining PR 1 scope by line count) — only the workflow / alarm / ECR pieces of Section A fall through to staging-only verification.

## Critical files

| Path | Change |
|------|--------|
| `bruno-tests/environments/staging.bru` | Fix baseUrl line 2 |
| `bruno-tests/README.md` (new) | Document canonical naming convention |
| `bruno-tests/admin-cleanup-api/` (new) | Authorization test collection (F2) |
| `bruno-tests/{file-upload,companies,users,tasks,partners}-api/00-pretest-cleanup.bru` + `99-posttest-cleanup.bru` (new) | Per-collection cleanup hooks for existing collections |
| `bruno-tests/{event-types,event-topics,events-crud,sessions,speaker-pool,event-full-workflow}-api/` (new, ×6) | Split from events-api |
| `bruno-tests/events-api/` | Removed after PR 2 |
| `scripts/ci/run-bruno-tests.sh` | Add `--collection`, `--cleanup-only`, `--no-bail`; remove speakers-api ghost; relax `set -e` around collection loop |
| `scripts/ci/rollback-deployment.sh` | `--yes`, `--dry-run`, switch to `staging-stable` ECR tag |
| `.github/workflows/deploy-staging.yml` | Split into 4 jobs; ECR retag steps; flip `continue-on-error` in PR 14 |
| `.github/workflows/deploy-production.yml:362-429` | Fix dead account-422940799530 reference |
| `infrastructure/lib/constructs/domain-service-construct.ts:299` | Add `deploymentAlarms` with statically-named alarms |
| `services/*/src/main/java/.../controller/TestFixtureCleanupController.java` (new, ×5) | Per-service cleanup endpoint |
| `scripts/db/bruno-staging-pre-cleanup.sql` (new) | One-time exact-match deletion of 17 legacy test rows (10 companies + 7 partners, with cascade dependents). Runs once when PR 1 deploys. |
| `docs/plans/bruno-staging-hardening.md` (this file) | Checked-in plan artifact |

### Service ownership mapping (discovered 2026-05-24)

All 5 microservices share the `public` schema. Only **3 services** own entity tables:

| Service | Owns tables |
|---------|-------------|
| **CUMS** (company-user-management-service) | companies, user_profiles, role_assignments, user_additional_emails, logos |
| **EMS** (event-management-service) | events, event_types, event_tasks, event_photos, event_teaser_images, topics, topic_usage_history, sessions, session_users, session_materials, session_content_history, session_timing_history, registrations, speaker_pool, speaker_invitation_tokens, speaker_outreach_history, speaker_arrivals, speaker_slot_preferences, speaker_reminder_log, speaker_status_history |
| **PCS** (partner-coordination-service) | partners, partner_meetings, partner_meeting_attendance, partner_meeting_rsvps, partner_notes, topic_suggestions, topic_votes |

Speaker-coordination-service and attendee-experience-service own NO tables (still foundation phase) — no `TestFixtureCleanupController` needed there. PR 1 ships 3 cleanup controllers, not 5.

Cross-service cleanup order (Bruno cleanup .bru files orchestrate):
1. PCS (deletes partner-side rows first — they reference companies + users by name)
2. EMS (deletes events + cascades — they reference users by name)
3. CUMS (foundation — last to delete, deletes companies + users + logos)

## Reuse — existing functions/utilities

- `scripts/auth/get-token.sh` + `refresh-token.sh` — already handle multi-role staging tokens.
- `AbstractIntegrationTest` (shared-kernel) — base for Java tests covering the cleanup controller.
- `E2ETestTokenController.java` — pattern reference for `TestFixtureCleanupController.java`, but use `@ConditionalOnProperty` + `@PreAuthorize` instead of `@Profile`.
- Bruno's existing `bru.setEnvVar()` / `bru.setVar()` pattern — keep for identifier handoff within collections.
- Existing `circuitBreaker: { rollback: true }` at `domain-service-construct.ts:243-246` — stays; A3 only ADDS `deploymentAlarms`.
- Existing per-collection `99-cleanup-test-*.bru` files — keep them, the new `99-posttest-cleanup.bru` runs *after* them as a final defense.

## Risks to track

1. **Staging account = production.** Every "test against staging" is "test against production." Cleanup endpoint is the only thing standing between Bruno and real data — per-entity regex prefix lock + role check + audit log + startup invariant warn are all required.
2. **First real cleanup run after A1's URL fix will likely delete pre-existing test junk** in the production-account DB (months of accumulated leftovers from runs that hit DNS-NXDOMAIN). Pre-audit: query rows matching each entity's canonical regex from B1 before merging PR 1 and confirm with user that everything matched is disposable. _(Pre-audit results table at the top of this file.)_
3. **`deploy-production.yml:362-429` references decommissioned account 422940799530.** Same-PR fix in A5.
4. **`scripts/ci/run-bruno-tests.sh:4 set -e`** silently masks token-loading failures. Audit before relying on the script as a deploy gate.
5. **Circuit breaker only catches startup failures.** Until A3's deployment alarms ship, Bruno is the only safety net for "container starts but serves 500s." Ship A3 in PR 1 so both arrive together.
6. **`speaker-pool-api` cleanup is cross-service** (events + CUMS). PR 11 (its audit) needs PR 1's CUMS cleanup endpoint to exist — sequencing is built in already, but flag if anything slips.
7. **Constraint surprises to verify in PR 1 — these are likely bugs the test work uncovers**:
   - `partners.company_name VARCHAR(12)` vs `companies.name VARCHAR(255)` is a real bug (partners can only reference companies whose names are ≤12 chars). Don't fix in this plan's scope, but file as a separate ticket on first hit. Leave a comment in `V2__create_partner_coordination_schema.sql:9`.
   - `companies-api.openapi.yml` says company name regex is `^[A-Za-z0-9]+$` (alphanumeric only) but `companies-api/01-create-company.bru` uses hyphens and tests pass. Either the OpenAPI is wrong or server validation isn't enforced — PR 1 reproduces and reports. Whichever is wrong is a bug-fix commit in PR 1 or follow-up.
   - `users-api` test data may not match the DB regex `^[a-z]+\.[a-z]+(\.[0-9]+)?$` (existing tests use hyphens — verify in PR 5's audit). If existing tests pass despite hyphens, the regex isn't enforced server-side: another bug to file.
   - Any other "test passes but shouldn't" mismatches discovered during the audit pass land as inline fix-commits in the relevant PR (per user: "fixing some bugs we prob. find on the way").
