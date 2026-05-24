# Plan: Harden Bruno API tests to gate staging deploys with auto-rollback

## Current status

> **Where we are:** Plan approved 2026-05-24. Parent branch pushed (`test-enhancement/bruno-staging-hardening`). Pre-PR-1 audit complete — 17 disposable rows identified, dispositions confirmed with user. Next: open PR 1 from `test-enhancement/bruno-staging-hardening-infra`.

Update this one line on every PR merge so anyone (including a fresh Claude session) can pick up the work without re-reading the whole plan.

## Progress log

This plan is **not** tracked as BMad stories — it's test infrastructure work + opportunistic bugfixes. The table below is the single source of truth for progress. Each PR's last commit before merge updates its row in this table.

| PR # | Branch | Scope | Status | Merged | Findings / bugs discovered |
|------|--------|-------|--------|--------|---------------------------|
| 1 | `test-enhancement/bruno-staging-hardening-infra` | Sections A + B: cleanup endpoints, runner flags, ECR `staging-stable` tag, deployment alarms, Bruno-failure rollback job, one-time legacy-junk cleanup script (17 rows), companies `@Pattern` validation fix. Plan doc lands here. | ⬜ not started | — | — |
| 2 | `test-enhancement/bruno-events-api-split` | Section C: decompose events-api into 6 collections | ⬜ not started | — | — |
| 3 | `test-enhancement/bruno-audit-file-upload-api` | D.1: light audit | ⬜ not started | — | — |
| 4 | `test-enhancement/bruno-audit-companies-api` | D.2: light audit | ⬜ not started | — | — |
| 5 | `test-enhancement/bruno-audit-users-api` | D.3: light audit | ⬜ not started | — | — |
| 6 | `test-enhancement/bruno-audit-tasks-api` | D.4: light audit | ⬜ not started | — | — |
| 7 | `test-enhancement/bruno-audit-event-types-api` | D.5: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 8 | `test-enhancement/bruno-audit-event-topics-api` | D.6: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 9 | `test-enhancement/bruno-audit-events-crud-api` | D.7: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 10 | `test-enhancement/bruno-audit-sessions-api` | D.8: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 11 | `test-enhancement/bruno-audit-speaker-pool-api` | D.9: light audit (new collection from PR 2, cross-service cleanup) | ⬜ not started | — | — |
| 12 | `test-enhancement/bruno-audit-event-full-workflow-api` | D.10: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 13 | `test-enhancement/bruno-audit-partners-api` | D.11: light audit | ⬜ not started | — | — |
| 14 | `test-enhancement/bruno-gate-flip` | Section E: remove `continue-on-error`, prove rollback path | ⬜ not started | — | — |

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

- **`companies.name` server-side validation NOT enforcing the OpenAPI pattern `^[A-Za-z0-9]+$`.** Audit found 7+ rows with spaces (e.g. `bruno test company 1761143046`, `E2E Test Company 1768749879358`). Either the OpenAPI spec is wrong or the controller doesn't validate. **Decision (user, 2026-05-24): fix in PR 1.** Add server-side `@Pattern(regexp="^[A-Za-z0-9]+$")` validation on the company-create/update DTO in `services/company-user-management-service/src/main/java/.../dto/CompanyDto.java` (or the equivalent generated DTO + controller validation). Verify with a unit test that `"bruno test company 123"` is rejected with 400.
- **`partners.company_name VARCHAR(12)` constraint confirmed enforced** — all 7 `brtest*` entries are ≤12 chars. Still the known mismatch with `companies.name VARCHAR(255)` from risk #7.1. **Decision (user, 2026-05-24): separate Linear ticket, not in PR 1.** Requires a migration to widen the column; out of scope for test infrastructure work.

### Cleanup approach for the historical junk — decided

**Decision (user, 2026-05-24): one-time SQL cleanup script in PR 1.** Targets the 17 specific rows by exact-match list (10 companies + 7 partners), not by regex. The cleanup endpoint's canonical regexes (B1) won't match these legacy patterns (`bruno test company 1761143046` has spaces; `E2E Test Company` is mixed case with spaces; `testag/testcompanya/testdeclinea` follow no canonical), and that's fine — historical patterns aren't worth supporting in the long-running endpoint.

PR 1 ships:
1. The cleanup endpoint (for ongoing canonical patterns going forward).
2. A one-shot SQL migration `V<n>__bruno_staging_pre_cleanup.sql` (or a dedicated `scripts/db/bruno-staging-pre-cleanup.sql`) that exact-match deletes the 17 rows + their cascade dependents (partner_meeting_*, topic_votes, partner_notes for the 7 partners). SELECT-then-DELETE pattern: SELECT first to log counts, then DELETE inside a transaction with a final COUNT verification.
3. Audit log entries in CloudWatch for each deletion.

The script runs exactly once when PR 1 deploys. Never re-runs.

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
| 1 | `test-enhancement/bruno-staging-hardening-infra` | A + B below. Cleanup endpoints, runner flags, ECR `staging-stable` tag, deployment alarms, Bruno-failure rollback job wired (still `continue-on-error: true`). One-time SQL pre-cleanup script for the 17 legacy rows. Server-side `@Pattern` validation fix on `companies.name`. Plan doc lands here. | warning |
| 2 | `test-enhancement/bruno-events-api-split` | C: decompose events-api into 6 collections | warning |
| 3-13 | `test-enhancement/bruno-audit-{entity}` | D: light audit pass on each collection in dependency order. One PR per collection. | warning |
| 14 | `test-enhancement/bruno-gate-flip` | E: remove `continue-on-error`. Prove rollback works with deliberate-fail test. | **blocking + auto-rollback** |

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
- `POST /api/v1/admin/test-fixtures/cleanup` per service.
- `@PreAuthorize("hasRole('ORGANIZER')")`.
- Body specifies `entityType` (companies | users | events | sessions | topics | tasks | partners | registrations | speaker_pool | uploads) and `prefix`.
- **Server-side constant `Map<EntityType, Pattern>`** validates the prefix against the entity-specific regex from B1 — request body cannot supply the pattern, only the literal prefix value to match. Reject anything that doesn't match the bound pattern for the given entity type. This prevents `prefix=*` / `prefix=BAT` / SQL-injection-shaped inputs at the controller layer.
- Audit-log every invocation (caller username, entityType, prefix, deletion counts).
- Per-service (not gateway fan-out) for blast-radius control.

**Cleanup order inside the endpoint** (respects FK constraints):
1. registrations → 2. session-speakers → 3. sessions → 4. speaker-pool entries → 5. event-topics → 6. tasks → 7. events → 8. topics → 9. partner-topic-votes/contacts/meetings → 10. partners → 11. users (matching `bruno.test.*`) → 12. companies → 13. file uploads (S3 + DB metadata).

**Event state machine:** dedicated `TestFixtureRepository.hardDeleteByPrefix(...)` issues raw `DELETE FROM events WHERE event_code LIKE 'BRUNO-TEST-%'` with cascade. Bypasses state guards, only reachable from this endpoint.

**Startup invariant check:** at boot, count real entities matching the canonical patterns. Log `WARN` if > 0 — don't fail-fast (might be junk from a previous failed run that this cleanup will sweep), but operators should see it.

### B3. Per-collection cleanup `.bru` files
Each collection gets:
- `00-pretest-cleanup.bru` — POST to cleanup endpoint with that entity's prefix. Status assertion `oneOf([200, 204, 404])` — never fails the run.
- `99-posttest-cleanup.bru` — same.

Bruno orders by filename → `00-` first, `99-` last. Two-layer defense: `run-bruno-tests.sh` ALSO invokes `--cleanup-only` after each collection regardless of pass/fail.

## C. events-api decomposition proposal (PR 2)

The only collection that genuinely needs splitting. Today `bruno-tests/events-api/` is 67 sequential tests covering 6 conceptually-distinct entity domains. Proposed split:

| New collection | Source tests | New tests needed | Auth |
|----------------|--------------|------------------|------|
| `event-types-api` | (none — currently untested) | All new: GET /types, idempotent PUT. **No create/delete** — API gap, not a test bug. Document the gap. | organizer |
| `event-topics-api` | 30, 30a, 31, 32, 33, 34, 67 | 00-/99- cleanup hooks. 30a (fixture event) becomes `00a-fixture-event.bru` | organizer |
| `events-crud-api` | 01, 03–07, 19, 29 | 00-/99- cleanup, DELETE-then-GET-404 | organizer |
| `sessions-api` | 08–11, 20–28 | 00-/99- cleanup, fixture event setup | organizer |
| `speaker-pool-api` | 35–56, 60–66 | 00-/99- cleanup. **Promote tests (43–48) provision real Users in CUMS** — cleanup must wipe `bruno.test.*` users via CUMS endpoint (cross-service) | organizer |
| `event-full-workflow-api` | 12, 13, 16 | Net-new tests advancing through all 8 states: CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED + registrations CRUD | organizer |

`speaker-portal-api` stays unchanged and dev-only. After the split, `bruno-tests/events-api/` directory ceases to exist (no overlap with the new collections).

The `speaker-pool-api` collection is the hairy one — its cleanup spans events service (pool entries) + CUMS (provisioned Users). Make sure CUMS cleanup endpoint from PR 1 supports the cross-service prefix wipe before merging PR 2.

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
| `services/company-user-management-service/src/main/java/.../dto/CompanyDto.java` (or equivalent) | Add server-side `@Pattern(regexp="^[A-Za-z0-9]+$")` on company name field; verify with unit test rejecting `"bruno test company 123"` |
| `docs/plans/bruno-staging-hardening.md` (this file) | Checked-in plan artifact |

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
